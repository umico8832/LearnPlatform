#!/usr/bin/env python3
"""Add a second isolated backend and an Nginx upstream; remove them before e2e-clean."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from environment import ROOT, require_lock, ensure_e2e_container


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--lease', default='30s')
    parser.add_argument('--quota', default='3')
    parser.add_argument('--down', action='store_true')
    args = parser.parse_args()
    require_lock()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    overlay = output / 'compose.multi.json'
    env = {**os.environ, 'COMPOSE_DISABLE_ENV_FILE':'1', 'DB_PASSWORD':'root123',
           'JWT_SECRET':'ci-e2e-jwt-secret-key-for-testing-only'}
    base = ['docker','compose','-f',str(ROOT/'docker-compose.yml'),'-f',str(ROOT/'docker-compose.e2e.yml')]
    if args.down:
        ensure_e2e_container('learnplatform-e2e-gateway', require_running=False)
        ensure_e2e_container('learnplatform-e2e-backend2', require_running=False)
        subprocess.run(base+['-f',str(overlay),'rm','-sf','gateway','backend2'], env=env, check=True)
        return
    # Resolve only explicit E2E fixtures and keep the generated configuration in memory.
    config = json.loads(subprocess.check_output(base+['config','--format','json'],env=env,text=True))
    backend = config['services']['backend']
    backend.pop('build',None)
    backend.pop('container_name',None)
    backend['container_name'] = 'learnplatform-e2e-backend2'
    backend['ports'] = ['127.0.0.1:18082:8080']
    backend['environment'].update(AI_DAILY_QUOTA=args.quota, AI_MODEL='deepseek-flash',
        AI_EXECUTION_LEASE=args.lease, SPRING_CACHE_TYPE='redis')
    nginx = output / 'nginx.conf'
    nginx.write_text('''events {}
http {
  upstream backend_pool { server backend:8080; server backend2:8080; }
  server { listen 80;
    location / { proxy_set_header Host $host; proxy_pass http://backend_pool; proxy_buffering off;
      proxy_read_timeout 180s; proxy_http_version 1.1;
      add_header X-Upstream $upstream_addr always;
    }
  }
}
''')
    content = {'services': {'backend2':backend, 'backend':{'environment':{
        'AI_DAILY_QUOTA':args.quota,'AI_EXECUTION_LEASE':args.lease}},
        'ai-mock': {'ports':['127.0.0.1:18084:80'], 'volumes':[f'{ROOT}/scripts/concurrency/ai-mock.py:/app/ai-mock.py:ro']},
        'gateway':{'image':'nginx:alpine','container_name':'learnplatform-e2e-gateway',
                   'depends_on':{'backend':{'condition':'service_healthy'},'backend2':{'condition':'service_healthy'}},
                   'ports':['127.0.0.1:18083:80'],'networks':['learn-platform'],
                   'volumes':[f'{nginx}:/etc/nginx/nginx.conf:ro']}}}
    overlay.write_text(json.dumps(content,indent=2))
    subprocess.run(base+['-f',str(output/'compose.loadtest.json'),'-f',str(overlay),
        'up','-d','--no-build','--force-recreate','--wait','--wait-timeout','180','backend','backend2','ai-mock','gateway'],
        cwd=ROOT,env=env,check=True)
    print('Two isolated backends ready behind 127.0.0.1:18083',flush=True)


if __name__ == '__main__':
    main()
