#!/usr/bin/env python3
"""Run k6 against the locked, isolated E2E stack; retain no auth material."""
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SOURCE = Path(__file__).resolve().parent
HERE = Path(os.environ['LOADTEST_OUTPUT']).resolve()
HERE.mkdir(parents=True, exist_ok=True)
BASE = os.environ.get('LOADTEST_BASE', 'http://127.0.0.1:18081')
K6 = Path(os.environ.get('K6_BIN', 'k6'))
from environment import ensure_http_target

fixture = {}
validated_targets = set()


def guard_target(base):
    if base not in validated_targets:
        ensure_http_target(base)
        validated_targets.add(base)


def api(path, body=None, token=None, method=None, key=None):
    guard_target(BASE)
    headers = {'Content-Type': 'application/json', 'Idempotency-Key': key or str(uuid.uuid4())}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(),
        headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as response:
        value = json.load(response)
    if value.get('code') != 0:
        raise RuntimeError(f'Fixture API failed: {path}, business code={value.get("code")}')
    return value.get('data')


def login(username, password):
    return api('/api/auth/login', {'account': username, 'password': password,
        'turnstileToken': 'XXXX.DUMMY.TOKEN.XXXX'})['token']


def create_user(admin, course_id, question_id, index):
    username = 'p28c' + uuid.uuid4().hex[:22]
    password = 'IsolatedPerfFixture!2026'
    api('/api/admin/users', {'username': username, 'password': password,
        'nickname': '隔离压测学习者', 'role': 'USER'}, admin)
    token = login(username, password)
    api(f'/api/my-courses/{course_id}', {}, token)
    attempt = api(f'/api/understanding/questions/{question_id}/attempts', {'answer': 'A'}, token)
    return {'token': token, 'attemptId': attempt['attemptId']}


class FixtureHandler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        if self.path != '/fixture':
            self.send_error(404)
            return
        data = json.dumps(fixture).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def discover_upstreams():
    if BASE != 'http://127.0.0.1:18083':
        return []
    found = set()
    for _ in range(8):
        with urllib.request.urlopen(BASE + '/api/public/health', timeout=10) as response:
            found.add(response.headers.get('X-Upstream', ''))
    if len(found) != 2 or '' in found:
        raise RuntimeError('Nginx must expose exactly two live upstream peers')
    return sorted(found)


def metrics_snapshot():
    names = ('hikaricp_connections_', 'executor_', 'jvm_threads_live_threads',
        'ai_admission_', 'jvm_memory_used_bytes', 'process_cpu_usage', 'system_cpu_usage', 'jvm_gc_pause_seconds')
    result = []
    for base in os.environ.get('LOADTEST_METRICS', BASE).split(','):
        guard_target(base)
        with urllib.request.urlopen(base + '/actuator/prometheus', timeout=10) as response:
            lines = response.read().decode().splitlines()
        result.append('# instance ' + base)
        result.extend(line for line in lines if line.startswith(names))
    return result


def run_case(mode, vus, label, duration='30s', iterations=3):
    result = HERE / f'{label}.json'
    if fixture.get('upstreams'):
        (HERE / 'upstream-peers.json').write_text(json.dumps(fixture['upstreams'], indent=2))
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'TMPDIR') if key in os.environ}
    env.update(MODE=mode, VUS=str(vus), DURATION=duration, ITERATIONS=str(iterations),
        FIXTURE_URL=fixture_url, RESULT_FILE=str(result), K6_NO_USAGE_REPORT='true',
        EXPECT_UPSTREAMS='1' if fixture.get('upstreams') else '0')
    observations = []
    stop = threading.Event()

    def sample():
        while not stop.is_set():
            try:
                observations.append({'time': time.time(), 'metrics': metrics_snapshot()})
            except Exception:
                observations.append({'time': time.time(), 'unavailable': True})
            stop.wait(1)

    sampler = threading.Thread(target=sample)
    sampler.start()
    start = time.monotonic()
    with (HERE / f'{label}.log').open('w') as log:
        proc = subprocess.run([str(K6), 'run', '--quiet', str(SOURCE / 'loadtest.js')], env=env,
            stdout=log, stderr=subprocess.STDOUT)
    elapsed = time.monotonic() - start
    stop.set()
    sampler.join()
    (HERE / f'{label}-observations.json').write_text(json.dumps(observations, indent=2))
    value = json.loads(result.read_text())
    metric = value['metrics']
    count = metric['true_failures']['values']['passes'] + metric['true_failures']['values']['fails']
    good = metric.get('successful_operations', {'values': {'count': 0}})['values']['count']
    row = {'case': label, 'vus': vus, 'requests': count, 'successful': good,
        'true_failure_rate': metric['true_failures']['values']['rate'],
        'endpoint_ms': metric['endpoint_ms']['values'], 'elapsed_wall_s': elapsed,
        'test_duration_ms': value['state']['testRunDurationMs'],
        'refusals': metric.get('rejected_operations', {'values': {'count': 0}})['values']['count'],
        'refusal_rate': metric['capacity_refusal_rate']['values']['rate'],
        'successful_ms': metric.get('successful_ms',{}).get('values'),
        'refused_ms': metric.get('refused_ms',{}).get('values'),
        'upstream_first_requests': metric.get('upstream_first_requests', {'values': {'count': 0}})['values']['count'],
        'upstream_second_requests': metric.get('upstream_second_requests', {'values': {'count': 0}})['values']['count'],
        'upstream_unknown_requests': metric.get('upstream_unknown_requests', {'values': {'count': 0}})['values']['count'],
        'http_transport_error_rate': metric['http_req_failed']['values']['rate'], 'k6_exit': proc.returncode}
    row['operations_per_s'] = count / (row['test_duration_ms'] / 1000)
    row['successful_per_s'] = good / (row['test_duration_ms'] / 1000)
    print(json.dumps(row, ensure_ascii=False), flush=True)
    return row


def content_probe(user, question_id):
    req = urllib.request.Request(BASE + f'/api/understanding/attempts/{user["attemptId"]}/ask/stream',
        data=json.dumps({'message': '请解释概念。', 'requestId': str(uuid.uuid4())}).encode(),
        headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + user['token']})
    start = time.monotonic()
    first = None
    first_status = None
    heartbeats = 0
    done = False
    with urllib.request.urlopen(req, timeout=180) as response:
        for line in response:
            if line.startswith(b'event:status') and first_status is None:
                first_status = time.monotonic() - start
            if line.startswith(b'event:heartbeat'):
                heartbeats += 1
            if line.startswith(b'event:content') and first is None:
                first = time.monotonic() - start
            if line.startswith(b'event:done'):
                done = True
    result = {'first_status_s': first_status, 'heartbeats': heartbeats, 'first_content_s': first, 'total_s': time.monotonic() - start, 'done': done}
    (HERE / 'sse-content-probe.json').write_text(json.dumps(result, indent=2))
    print('SSE content probe:', result, flush=True)


def replay_probe(user, question_id):
    request_key = str(uuid.uuid4())
    def submit(_):
        return api('/api/review/submit', {'questionId': question_id,
            'userAnswer': 'A', 'selfAssessedQuality': 4}, user['token'], key=request_key)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(submit, range(2)))
    output = [{'totalReviews': r.get('totalReviews'), 'questionId': r.get('questionId')} for r in responses]
    (HERE / 'review-replay-probe.json').write_text(json.dumps(output, indent=2))
    print('Review duplicate-body responses:', output, flush=True)


if __name__ == '__main__':
    admin = login('admin', 'admin123')
    courses = api('/api/courses/list', token=admin)
    course = next(c for c in courses if c['name'] == '408 数据结构')
    question_id = None
    for page in range(1, 21):
        questions = api(f'/api/questions?courseId={course["id"]}&pageNum={page}&pageSize=100', token=admin)
        for question in questions['records']:
            if question['content'] == '入栈顺序为 1,2,3,4,5，下列哪个是可能的出栈序列？':
                question_id = question['id']
        if question_id or page >= questions['pages']:
            break
    assert question_id, 'Reviewed E2E question fixture unavailable'
    print('Creating 64 isolated learners; auth stays in memory.', flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        users = list(pool.map(lambda i: create_user(admin, course['id'], question_id, i), range(64)))
    fixture.update(base=BASE, questionId=question_id, users=users, upstreams=discover_upstreams())
    (HERE / 'fixture-metadata.json').write_text(json.dumps({'course_count': len(courses),
        'course_id': course['id'], 'question_id': question_id, 'learners': len(users)}, indent=2))
    server = ThreadingHTTPServer(('127.0.0.1', 0), FixtureHandler)
    fixture_url = f'http://127.0.0.1:{server.server_port}'
    threading.Thread(target=server.serve_forever, daemon=True).start()
    rows = []
    try:
        if '--ask-only' not in sys.argv:
            run_case('courses', 4, 'warmup-courses', '10s')
            run_case('understanding', 4, 'warmup-understanding', '10s')
            run_case('submit', 4, 'warmup-submit', '10s')
            for mode in ('courses', 'understanding', 'submit'):
                for vus in (10, 50):
                    rows.append(run_case(mode, vus, f'{mode}-{vus}'))
        else:
            rows = [r for r in json.loads((HERE / 'summary.json').read_text()) if not r['case'].startswith('ask-')]
        content_probe(users[-1], question_id)
        replay_probe(users[-1], question_id)
        rows.append(run_case('ask', 8, 'ask-8'))
        # Fresh identities keep the production per-question limit of 5 unchanged.
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            users = list(pool.map(lambda i: create_user(admin, course['id'], question_id, i), range(64)))
        fixture['users'] = users
        rows.append(run_case('ask', 64, 'ask-64'))
    finally:
        (HERE / 'summary.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2))
        server.shutdown()
        fixture.clear()

    if any(row['k6_exit'] != 0 for row in rows):
        raise SystemExit(1)
