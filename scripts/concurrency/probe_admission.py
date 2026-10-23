#!/usr/bin/env python3
"""Exercise real two-process AI leases, renewal, cancellation and SIGKILL recovery."""
import concurrent.futures
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
import run_suite as suite
from environment import require_lock, ensure_http_target, ensure_e2e_container
from database import sql
from probe_consistency import fixture_question

BASES = ['http://127.0.0.1:18081', 'http://127.0.0.1:18082']
MOCK = 'http://127.0.0.1:18084'
KEY = 'learnplatform:ai:admission:leases'


def read_json(url):
    ensure_http_target(MOCK)
    with urllib.request.urlopen(url, timeout=10) as response:
        return json.load(response)


def redis(command):
    require_lock()
    project = subprocess.check_output(['docker','inspect','--format',
        '{{ index .Config.Labels "com.docker.compose.project" }}','learnplatform-e2e-redis'],text=True).strip()
    if project != 'learnplatform-e2e':
        raise RuntimeError('Wrong Redis project')
    return subprocess.check_output(['docker','exec','learnplatform-e2e-redis','redis-cli','--raw',*command],text=True).strip()


def wait_until(test, seconds=20):
    end = time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            if test():
                return
        except (OSError, ValueError):
            pass
        time.sleep(0.1)
    raise RuntimeError('Timed out waiting for isolated probe condition')


def control(delay):
    wait_until(lambda: read_json(MOCK+'/stats')['active']==0)
    ensure_http_target(MOCK)
    request=urllib.request.Request(MOCK+'/control',json.dumps({'delay':delay}).encode(),
        {'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=10):
        pass


def ask_request(user, base, request_id=None):
    return urllib.request.Request(base+f'/api/understanding/attempts/{user["attemptId"]}/ask/stream',
        json.dumps({'message':'请说明应该关注的概念。','requestId':request_id or str(uuid.uuid4())}).encode(),
        {'Content-Type':'application/json','Authorization':'Bearer '+user['token']})


def ask(user, base, request_id=None):
    started=time.monotonic()
    heartbeats=0
    try:
        with urllib.request.urlopen(ask_request(user,base,request_id),timeout=90) as response:
            body=response.read().decode()
            heartbeats=len(re.findall(r'event:\s*heartbeat',body))
        if re.search(r'event:\s*done',body) and not re.search(r'event:\s*error',body):
            outcome='success'
        else:
            try:
                code=json.loads(body).get('code')
            except json.JSONDecodeError:
                codes=re.findall(r'"code"\s*:\s*(\d+)',body)
                code=int(codes[-1]) if codes else None
            outcome='rejected' if code==1007 else 'failure'
    except (OSError, urllib.error.HTTPError, http.client.HTTPException):
        outcome='disconnected'
    return {'outcome':outcome,'seconds':time.monotonic()-started,'heartbeats':heartbeats}


def inflight():
    counts=[]
    for base in BASES:
        with urllib.request.urlopen(base+'/actuator/prometheus',timeout=3) as response:
            text=response.read().decode()
        counts.append(float(re.search(r'^ai_admission_inflight(?:\{[^}]*\})?\s+([+-]?[\d.]+)',text,re.M).group(1)))
    return counts


def sample_during(action):
    samples=[]
    stop=threading.Event()
    def sample():
        while not stop.is_set():
            try:
                values=inflight()
                samples.append({'time':time.time(),'instances':values,'sum':sum(values)})
            except (OSError,AttributeError):
                pass
            stop.wait(.1)
    thread=threading.Thread(target=sample)
    thread.start()
    try:
        result=action()
    finally:
        stop.set()
        thread.join()
    return result,samples


class EvidenceRows(list):
    def append(self, value):
        super().append(value)
        path=Path(os.environ['LOADTEST_OUTPUT'])/'admission-progress.json'
        path.write_text(json.dumps(list(self),indent=2))


def main():
    require_lock()
    output=Path(os.environ['LOADTEST_OUTPUT'])
    output.mkdir(parents=True,exist_ok=True)
    suite.BASE='http://127.0.0.1:18083'
    admin=suite.login('admin','admin123')
    course,question=fixture_question(suite,admin)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        users=list(pool.map(lambda n:suite.create_user(admin,course,question,n),range(48)))
    rows=EvidenceRows()
    control(1)
    def capacity():
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
            return list(pool.map(lambda n:ask(users[n],BASES[n%2]),range(16)))
    results,samples=sample_during(capacity)
    mock=read_json(MOCK+'/stats')
    counts={name:sum(r['outcome']==name for r in results) for name in ('success','rejected','failure')}
    rows.append({'probe':'cross-instance-capacity','counts':counts,'mock_peak':mock['peak'],
        'sampled_inflight_peak':max(s['sum'] for s in samples),
        'passed':counts=={'success':8,'rejected':8,'failure':0} and mock['peak']==8 and max(s['sum'] for s in samples)==8})
    (output/'capacity-samples.json').write_text(json.dumps(samples,indent=2))
    (output/'capacity-mock.json').write_text(json.dumps(mock,indent=2))
    # Run with AI_EXECUTION_LEASE=3s. A complete 8-second response must retain its permit.
    control(4)
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        future=pool.submit(ask,users[20],BASES[0])
        wait_until(lambda:int(redis(['ZCARD',KEY]))==1)
        time.sleep(4)
        retained=int(redis(['ZCARD',KEY]))
        result=future.result(90)
    rows.append({'probe':'stream-renewal','lease_s':3,'checked_after_s':4,'retained':retained,
        'duration_s':result['seconds'],'heartbeats':result['heartbeats'],'passed':retained==1 and result['outcome']=='success' and result['seconds']>6})
    control(4)
    request_id=str(uuid.uuid4())
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        future=pool.submit(ask,users[21],BASES[0],request_id)
        wait_until(lambda:int(redis(['ZCARD',KEY]))==1 and read_json(MOCK+'/stats')['active']==1)
        cancelled=time.monotonic()
        request=urllib.request.Request(BASES[1]+f'/api/understanding/attempts/{users[21]["attemptId"]}/ask/{request_id}',
            headers={'Authorization':'Bearer '+users[21]['token']},method='DELETE')
        with urllib.request.urlopen(request,timeout=10) as response:
            stop=json.load(response)
        wait_until(lambda:int(redis(['ZCARD',KEY]))==0 and sum(inflight())==0,seconds=15)
        ended=future.result(30)
    state=sql(f'SELECT status FROM question_ask_record WHERE attempt_id={int(users[21]["attemptId"])} ORDER BY id DESC LIMIT 1;').splitlines()[-1]
    rows.append({'probe':'cross-instance-cancel-release','protocol':'DELETE on second backend; stream on first',
        'reclaim_s':time.monotonic()-cancelled,'state':state,'inflight':sum(inflight()),
        'passed':stop['code']==0 and stop['data']['stopped'] and state=='CANCELLED' and ended['outcome']!='success' and sum(inflight())==0})
    control(2)
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
            killed=[pool.submit(ask,users[24+n],BASES[0]) for n in range(8)]
            wait_until(lambda:int(redis(['ZCARD',KEY]))==8)
            wait_until(lambda:read_json(MOCK+'/stats')['active']==8)
            ensure_e2e_container('learnplatform-e2e-backend', require_running=True)
            subprocess.run(['docker','kill','learnplatform-e2e-backend'],check=True,capture_output=True)
            start=time.monotonic()
            blocked=ask(users[40],BASES[1])
            wait_until(lambda:int(redis(['ZCARD',KEY]))==0,seconds=8)
            reclaimed=time.monotonic()-start
            recovered=list(pool.map(lambda n:ask(users[40+n],BASES[1]),range(8)))
            terminated=[future.result(10) for future in killed]
        rows.append({'probe':'killed-instance-recovery','lease_s':3,'reclaim_s':reclaimed,
            'pre_expiry':blocked['outcome'],'recovered_successes':sum(r['outcome']=='success' for r in recovered),
            'terminated_without_done':sum(r['outcome']!='success' for r in terminated),
            'passed':blocked['outcome']=='rejected' and all(r['outcome']=='success' for r in recovered) and reclaimed<8 and all(r['outcome']!='success' for r in terminated)})
    finally:
        ensure_e2e_container('learnplatform-e2e-backend', require_running=False)
        subprocess.run(['docker','start','learnplatform-e2e-backend'],check=True,capture_output=True)
        wait_until(lambda:urllib.request.urlopen(BASES[0]+'/api/public/health',timeout=2).status==200,seconds=90)
        control(1)
    control(1)
    try:
        ensure_e2e_container('learnplatform-e2e-redis', require_running=True)
        subprocess.run(['docker','stop','learnplatform-e2e-redis'],check=True,capture_output=True)
        unavailable=ask(users[22],BASES[1])
    finally:
        ensure_e2e_container('learnplatform-e2e-redis', require_running=False)
        subprocess.run(['docker','start','learnplatform-e2e-redis'],check=True,capture_output=True)
        wait_until(lambda:redis(['PING'])=='PONG',seconds=30)
    recovered=ask(users[23],BASES[1])
    rows.append({'probe':'redis-outage-fails-closed','during_outage':unavailable['outcome'],
        'rejection_s':unavailable['seconds'],'after_recovery':recovered['outcome'],
        'passed':unavailable['outcome']=='rejected' and unavailable['seconds']<3 and recovered['outcome']=='success'})
    result={'passed':all(row['passed'] for row in rows),'probes':rows}
    (output/'admission.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result,ensure_ascii=False),flush=True)
    return 0 if result['passed'] else 1


if __name__=='__main__':
    raise SystemExit(main())
