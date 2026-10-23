#!/usr/bin/env python3
"""Probe cross-instance invariants through two backends; never serialize credentials or bodies."""
import argparse
import concurrent.futures
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

SOURCE = Path(__file__).resolve().parent
sys.path.insert(0, str(SOURCE))
from environment import ensure_http_target
from database import CONTAINER, sql  # noqa: E402 -- this module enforces the E2E lock and Compose label.


validated_targets = set()


def request(base, path, body=None, token=None, method=None, key=None, timeout=45):
    if base not in validated_targets:
        ensure_http_target(base)
        validated_targets.add(base)
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    req = urllib.request.Request(base + path, None if body is None else json.dumps(body).encode(), headers,
                                 method=method or ('POST' if body is not None else 'GET'))
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.status
            content_type = response.headers.get_content_type()
            if content_type == 'text/event-stream':
                stream = response.read().decode(errors='replace')
                if re.search(r'event:\s*done', stream) and not re.search(r'event:\s*error', stream):
                    payload = {'code': 0, 'data': {}}
                else:
                    codes = re.findall(r'"code"\s*:\s*(\d+)', stream)
                    payload = {'code': int(codes[-1]) if codes else None}

            else:
                payload = json.load(response)
    except urllib.error.HTTPError as error:
        status = error.code
        try:
            payload = json.load(error)
        except (json.JSONDecodeError, UnicodeDecodeError):
            payload = {}
    return {'http': status, 'code': payload.get('code'), 'data': payload.get('data')}


def ok(result):
    return result['http'] == 200 and result['code'] == 0


def parallel(*calls):
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(calls)) as pool:
        return list(pool.map(lambda call: call(), calls))


def scalar(statement):
    lines = [line for line in sql(statement).splitlines() if line.strip()]
    return int(lines[-1].split('\t')[0])


class MysqlBarrier:
    """Hold one row lock in the guarded E2E MySQL, then release competing HTTP requests."""
    def __init__(self, table, row_id):
        self.process = None
        self.table, self.row_id = table, int(row_id)

    def __enter__(self):
        sql('SELECT 1;')  # validates the shared lock and Compose project before opening a held session.
        self.process = subprocess.Popen(
            ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=root123', CONTAINER,
             'mysql', '-uroot', '--batch', '--unbuffered', '--skip-column-names', 'learn_platform'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.process.stdin.write(f'START TRANSACTION; SELECT id FROM {self.table} WHERE id={self.row_id} FOR UPDATE;\n')
        self.process.stdin.flush()
        if not self.process.stdout.readline().strip():
            raise RuntimeError('Could not acquire E2E row-lock barrier')
        return self

    def __exit__(self, *_):
        if self.process:
            self.process.stdin.write('COMMIT;\n')
            self.process.stdin.flush()
            self.process.communicate(timeout=15)


def result_row(name, passed, **facts):
    return {'probe': name, 'passed': bool(passed), **facts}


def wait_for_lock():
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if scalar('SELECT COUNT(*) FROM performance_schema.data_lock_waits;') >= 2:
            return
        time.sleep(.1)
    raise RuntimeError('Expected HTTP request did not wait on the E2E row-lock barrier')


def submission_body(course_id, suffix):
    return {'content': 'G 跨实例隔离投稿 ' + suffix, 'questionType': 'FILL_BLANK', 'courseId': course_id,
            'difficulty': 1, 'correctAnswer': 'fixture', 'analysis': '隔离探针，不进入题库内容。'}


def create_course(admin, gateway):
    result = request(gateway, '/api/admin/courses', {'name': 'G probe ' + uuid.uuid4().hex[:16],
                     'description': 'isolated concurrency probe', 'sortOrder': 0}, admin)
    if not ok(result):
        raise RuntimeError('Cannot create isolated probe course')
    return result['data']['id']


def create_point(admin, gateway, course_id, parent_id=0):
    result = request(gateway, '/api/admin/knowledge-points', {'courseId': course_id, 'parentId': parent_id,
                     'name': 'G probe ' + uuid.uuid4().hex[:12], 'description': 'isolated', 'sortOrder': 0}, admin)
    if not ok(result):
        raise RuntimeError('Cannot create isolated probe knowledge point')
    return result['data']['id']


def create_learner(admin, gateway, course_id, question_id):
    import run_suite  # imported after LOADTEST_OUTPUT/LOADTEST_BASE are initialized in main.
    run_suite.BASE = gateway
    return run_suite.create_user(admin, course_id, question_id, 0)


def facts(user_id):
    tables = ('practice_record', 'fsrs_review_event', 'course_learning_event', 'gamification_xp_ledger')
    query = ' UNION ALL '.join(f"SELECT '{table}',COUNT(*) FROM {table} WHERE user_id={int(user_id)}" for table in tables)
    rows = sql(query).splitlines()[1:]
    return {name: int(count) for name, count in (row.split('\t') for row in rows)}


def fact_delta(before, after):
    return {name: after[name]-count for name, count in before.items()}


def idempotency_probes(first, second, gateway, admin, user, course_id, question_id):
    answer_key = str(uuid.uuid4())
    answer_body = {'answer': 'A', 'answerTime': 3}
    before_answer = facts(user['user_id'])
    answers = parallel(
        lambda: request(first, f'/api/understanding/questions/{question_id}/attempts', answer_body, user['token'], key=answer_key),
        lambda: request(second, f'/api/understanding/questions/{question_id}/attempts', answer_body, user['token'], key=answer_key))
    answer_delta = fact_delta(before_answer, facts(user['user_id']))
    answer_ids = {row['data'].get('attemptId') for row in answers if ok(row) and isinstance(row['data'], dict)}
    submission_key = str(uuid.uuid4())
    submissions = parallel(
        lambda: request(first, '/api/submission', submission_body(course_id, 'idempotency'), user['token'], key=submission_key),
        lambda: request(second, '/api/submission', submission_body(course_id, 'idempotency'), user['token'], key=submission_key))
    submission_ids = {row['data'].get('id') for row in submissions if ok(row) and isinstance(row['data'], dict)}
    practice_key = str(uuid.uuid4())
    practice_body = {'questionId': question_id, 'userAnswer': 'A', 'answerTime': 3}
    before_practice = facts(user['user_id'])
    practices = parallel(
        lambda: request(first, '/api/practice/submit', practice_body, user['token'], key=practice_key),
        lambda: request(second, '/api/practice/submit', practice_body, user['token'], key=practice_key))
    practice_delta = fact_delta(before_practice, facts(user['user_id']))
    same_practice = len(practices) == 2 and all(ok(row) for row in practices) and practices[0]['data'] == practices[1]['data']
    review_key = str(uuid.uuid4())
    before_review = facts(user['user_id'])
    reviews = parallel(
        lambda: request(first, '/api/review/submit', practice_body, user['token'], key=review_key),
        lambda: request(second, '/api/review/submit', practice_body, user['token'], key=review_key))
    review_delta = fact_delta(before_review, facts(user['user_id']))
    same_review = all(ok(row) for row in reviews) and reviews[0]['data'] == reviews[1]['data']
    return [result_row('answer-idempotency', len(answer_ids) == 1 and all(ok(row) for row in answers) and all(n == 1 for n in answer_delta.values()),
                       requests=len(answers), distinct_results=len(answer_ids), fact_delta=answer_delta),
            result_row('submission-idempotency', len(submission_ids) == 1 and all(ok(row) for row in submissions),
                       requests=len(submissions), distinct_results=len(submission_ids)),
            result_row('practice-idempotency', same_practice and all(n == 1 for n in practice_delta.values()), requests=len(practices), fact_delta=practice_delta),
            result_row('review-idempotency', same_review and all(n == 1 for n in review_delta.values()), requests=len(reviews), fact_delta=review_delta)]



def submission_atomicity(first, second, gateway, admin, user, course_id):
    made = request(gateway, '/api/submission', submission_body(course_id, 'review'), user['token'], key=str(uuid.uuid4()))
    if not ok(made):
        raise RuntimeError('Cannot create review probe submission')
    submission_id = made['data']['id']
    reviews = parallel(lambda: request(first, f'/api/admin/submission/{submission_id}/review', {'status': 1}, admin),
                       lambda: request(second, f'/api/admin/submission/{submission_id}/review', {'status': 2}, admin))
    import_candidate = request(gateway, '/api/submission', submission_body(course_id, 'import'), user['token'], key=str(uuid.uuid4()))
    if not ok(import_candidate):
        raise RuntimeError('Cannot create import probe submission')
    import_id = import_candidate['data']['id']
    approved = request(gateway, f'/api/admin/submission/{import_id}/review', {'status': 1}, admin)
    if not ok(approved):
        raise RuntimeError('Cannot approve controlled import probe submission')
    questions_before = scalar('SELECT COUNT(*) FROM question;')
    imported = parallel(lambda: request(first, f'/api/admin/submission/{import_id}/import', {}, admin),
                        lambda: request(second, f'/api/admin/submission/{import_id}/import', {}, admin))
    imported_count = scalar(f'SELECT COUNT(*) FROM question_submission WHERE id={int(import_id)} AND imported_question_id IS NOT NULL;')
    question_delta = scalar('SELECT COUNT(*) FROM question;') - questions_before
    return [result_row('opposite-review', sum(ok(row) for row in reviews) == 1,
                       requests=len(reviews), successes=sum(ok(row) for row in reviews)),
            result_row('single-import', sum(ok(row) for row in imported) == 1 and imported_count == 1 and question_delta == 1,
                       requests=len(imported), successes=sum(ok(row) for row in imported), imported_rows=imported_count, new_question_rows=question_delta)]


def parent_lock_probes(first, second, gateway, admin, course_id):
    course_id = create_course(admin, gateway)
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        with MysqlBarrier('course', course_id):
            child = pool.submit(request, first, '/api/admin/knowledge-points', {'courseId': course_id, 'parentId': 0,
                                'name': 'G race child', 'description': 'isolated', 'sortOrder': 0}, admin)
            deletion = pool.submit(request, second, f'/api/admin/courses/{course_id}', {}, admin, 'DELETE')
            wait_for_lock()
        child, deletion = child.result(45), deletion.result(45)
    live_course = scalar(f'SELECT COUNT(*) FROM course WHERE id={course_id} AND deleted=0;')
    live_children = scalar(f'SELECT COUNT(*) FROM knowledge_point WHERE course_id={course_id} AND deleted=0;')
    results.append(result_row('course-parent-lock', (live_course == 1 and live_children >= 1) or (live_course == 0 and live_children == 0),
                              create_success=ok(child), delete_success=ok(deletion), live_course=live_course, live_children=live_children))
    knowledge_course = create_course(admin, gateway)
    parent = create_point(admin, gateway, knowledge_course)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        with MysqlBarrier('knowledge_point', parent):
            child = pool.submit(request, first, '/api/admin/knowledge-points', {'courseId': knowledge_course, 'parentId': parent,
                                'name': 'G race grandchild', 'description': 'isolated', 'sortOrder': 0}, admin)
            deletion = pool.submit(request, second, f'/api/admin/knowledge-points/{parent}', {}, admin, 'DELETE')
            wait_for_lock()
        child, deletion = child.result(45), deletion.result(45)
    live_parent = scalar(f'SELECT COUNT(*) FROM knowledge_point WHERE id={parent} AND deleted=0;')
    live_children = scalar(f'SELECT COUNT(*) FROM knowledge_point WHERE parent_id={parent} AND deleted=0;')
    results.append(result_row('knowledge-parent-lock', (live_parent == 1 and live_children >= 1) or (live_parent == 0 and live_children == 0),
                              create_success=ok(child), delete_success=ok(deletion), live_parent=live_parent, live_children=live_children))
    return results


def quota_attempts(gateway, user, course_id, initial_question):
    import run_suite
    attempts = [user['attemptId']]
    for page in range(1, 21):
        questions = run_suite.api(f'/api/questions?courseId={course_id}&pageNum={page}&pageSize=100', token=user['token'])
        for question in questions['records']:
            if question['id'] == initial_question or len(attempts) == 4:
                continue
            if not ok(request(gateway, f'/api/understanding/questions/{question["id"]}', token=user['token'])):
                continue
            made = request(gateway, f'/api/understanding/questions/{question["id"]}/attempts',
                           {'answer': 'A', 'answerTime': 1}, user['token'], key=str(uuid.uuid4()))
            if ok(made):
                attempts.append(made['data']['attemptId'])
            if len(attempts) == 4:
                return attempts
        if page >= questions['pages']:
            break
    raise RuntimeError('Need four reviewed question attempts to isolate the daily quota from per-question limits')


def quota_probe(first, second, user, attempts):
    request_ids = [str(uuid.uuid4()) for _ in range(4)]
    calls = [lambda base=base, request_id=request_id, attempt_id=attempt_id: request(base, f'/api/understanding/attempts/{attempt_id}/ask/stream',
             {'message': 'probe quota', 'requestId': request_id}, user['token'], timeout=120)
             for base, request_id, attempt_id in zip((first, second, first, second), request_ids, attempts)]
    responses = parallel(*calls)
    successes = sum(ok(row) for row in responses)
    refusals = sum(row['code'] == 1006 for row in responses)
    used = scalar(f'''SELECT COUNT(*) FROM ai_call_log l WHERE l.user_id={int(user["user_id"])}
        AND (l.function_type<>'question_ask' OR l.run_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM ai_call_log first_call WHERE first_call.user_id=l.user_id
            AND first_call.function_type='question_ask' AND first_call.run_id=l.run_id AND first_call.id<l.id));''')
    return result_row('daily-ai-quota', successes == 3 and refusals == 1 and used == 3,
                      requests=len(responses), successes=successes, daily_quota_refusals=refusals, quota_records=used)


def fixture_question(run_suite, admin):
    courses = run_suite.api('/api/courses/list', token=admin)
    course = next(course for course in courses if course['name'] == '408 数据结构')
    for page in range(1, 21):
        questions = run_suite.api(f'/api/questions?courseId={course["id"]}&pageNum={page}&pageSize=100', token=admin)
        for question in questions['records']:
            if question['content'] == '入栈顺序为 1,2,3,4,5，下列哪个是可能的出栈序列？':
                return course['id'], question['id']
        if page >= questions['pages']:
            break
    raise RuntimeError('Reviewed E2E question fixture unavailable')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--first', default='http://127.0.0.1:18081')
    parser.add_argument('--second', default='http://127.0.0.1:18082')
    parser.add_argument('--gateway', default='http://127.0.0.1:18083')
    parser.add_argument('--skip-quota', action='store_true')
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault('LOADTEST_OUTPUT', str(args.output.parent))
    os.environ.setdefault('LOADTEST_BASE', args.gateway)
    import run_suite
    run_suite.BASE = args.gateway
    admin = run_suite.login('admin', 'admin123')
    fixture_course, question_id = fixture_question(run_suite, admin)
    course_id = create_course(admin, args.gateway)
    user = create_learner(admin, args.gateway, fixture_course, question_id)
    user['user_id'] = scalar(f"SELECT user_id FROM question_understanding_attempt WHERE id={int(user['attemptId'])};")
    rows = idempotency_probes(args.first, args.second, args.gateway, admin, user, course_id, question_id)
    rows += submission_atomicity(args.first, args.second, args.gateway, admin, user, course_id)
    rows += parent_lock_probes(args.first, args.second, args.gateway, admin, course_id)
    if not args.skip_quota:
        rows.append(quota_probe(args.first, args.second, user,
                                quota_attempts(args.gateway, user, fixture_course, question_id)))
    output = {'passed': all(row['passed'] for row in rows), 'probes': rows}
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2))
    print(json.dumps({'passed': output['passed'], 'probes': len(rows)}, ensure_ascii=False))
    return 0 if output['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
