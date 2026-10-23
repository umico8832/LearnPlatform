#!/usr/bin/env python3
"""Build a locked E2E stack with deterministic upstream; never use the development database."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def require_lock():
    common = subprocess.check_output(['git', 'rev-parse', '--git-common-dir'], cwd=ROOT, text=True).strip()
    main = (ROOT / common).resolve().parent
    lock = main / 'tmp/p28/e2e.lock'
    if not lock.is_dir() or not (lock / 'owner').read_text().startswith('codex G '):
        raise RuntimeError('Acquire the shared E2E lock as codex G before using this tool')


def validate_http_base(base):
    targets = {'http://127.0.0.1:18081':'learnplatform-e2e-backend',
               'http://127.0.0.1:18082':'learnplatform-e2e-backend2',
               'http://127.0.0.1:18083':'learnplatform-e2e-gateway',
               'http://127.0.0.1:18084':'learnplatform-e2e-ai-mock-1'}
    if base not in targets:
        raise RuntimeError('Refusing HTTP outside the isolated loopback ports')
    return targets[base]


def ensure_e2e_container(container, require_running=True):
    require_lock()
    state = subprocess.check_output(['docker','inspect','--format',
        '{{ index .Config.Labels "com.docker.compose.project" }}|{{.State.Running}}',container],text=True).strip()
    project, running = state.split('|')
    if project != 'learnplatform-e2e' or (require_running and running != 'true'):
        raise RuntimeError('Refusing to operate outside an owned E2E container')


def ensure_http_target(base):
    ensure_e2e_container(validate_http_base(base))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--keep-data', action='store_true', help='Rebuild apps while preserving the owned E2E volume')
    args = parser.parse_args()
    require_lock()
    source, output = args.source.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location('lifecycle', source / 'scripts/docker-lifecycle.py')
    life = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = life
    spec.loader.exec_module(life)
    os.environ.update(COMPOSE_DISABLE_ENV_FILE='1', DB_PASSWORD='root123',
                      JWT_SECRET='ci-e2e-jwt-secret-key-for-testing-only')
    env, _ = life.lifecycle_environment(e2e_runtime=True)
    override = {'services': {
        'ai-mock': {'volumes': [f'{Path(__file__).with_name("ai-mock.py")}:/app/ai-mock.py:ro']},
        'backend': {'ports': ['127.0.0.1:18081:8080'], 'environment': {
            'CACHE_TYPE': 'redis', 'SPRING_CACHE_TYPE': 'redis', 'AI_MODEL': 'deepseek-flash'}}}}
    overlay = output / 'compose.loadtest.json'
    overlay.write_text(json.dumps(override, indent=2))
    command = ['docker', 'compose', '-f', str(source / 'docker-compose.yml'), '-f',
               str(source / 'docker-compose.e2e.yml'), '-f', str(overlay)]
    life.require_docker()
    if not args.keep_data:
        life.reset_e2e(env)
    subprocess.run(command + ['up', '-d', '--build', '--force-recreate', '--wait',
                             '--wait-timeout', '240', 'backend', 'frontend'], cwd=source, env=env, check=True)
    print('Isolated stack ready: 127.0.0.1:18081', flush=True)


if __name__ == '__main__':
    main()
