"""Allow SQL only against the owned, explicitly named isolated Compose database."""
import subprocess
from environment import require_lock

CONTAINER = 'learnplatform-e2e-mysql'


def validate_project(project):
    if project != 'learnplatform-e2e':
        raise RuntimeError('Refusing SQL outside learnplatform-e2e')


def sql(statement):
    require_lock()
    project = subprocess.check_output(['docker', 'inspect', '--format',
        '{{ index .Config.Labels "com.docker.compose.project" }}', CONTAINER], text=True).strip()
    validate_project(project)
    result = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD=root123', CONTAINER,
        'mysql', '-uroot', '--batch', 'learn_platform'], input=statement, text=True,
        capture_output=True)
    if result.returncode:
        raise RuntimeError('Isolated SQL failed: ' + result.stderr.strip())
    return result.stdout
