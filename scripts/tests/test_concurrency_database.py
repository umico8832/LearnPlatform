"""The load fixture must reject every database project except the isolated E2E one."""
import pathlib
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'concurrency'))
from database import validate_project
from environment import validate_http_base, ensure_e2e_container


class ConcurrencyDatabaseTest(unittest.TestCase):
    def test_exact_project_required(self):
        validate_project('learnplatform-e2e')
        for value in ('learnplatform', '', 'other', 'learnplatform-e2e-copy'):
            with self.assertRaises(RuntimeError):
                validate_project(value)


class ConcurrencyHttpTargetTest(unittest.TestCase):
    def test_http_urls_are_limited_to_the_isolated_loopback_ports(self):
        for port in (18081, 18082, 18083, 18084):
            validate_http_base(f'http://127.0.0.1:{port}')
        for base in ('http://127.0.0.1:8080', 'https://example.com',
                     'http://127.0.0.1:18081@elsewhere', 'http://127.0.0.1:18083/api'):
            with self.assertRaises(RuntimeError):
                validate_http_base(base)

    @patch('environment.require_lock')
    @patch('environment.subprocess.check_output')
    def test_container_operations_require_project_and_running_state(self, inspect, lock):
        for state in ('learnplatform|true', 'other|false', 'learnplatform-e2e|false'):
            inspect.return_value=state
            with self.assertRaises(RuntimeError):
                ensure_e2e_container('learnplatform-e2e-backend')
        inspect.return_value='learnplatform-e2e|false'
        ensure_e2e_container('learnplatform-e2e-backend', require_running=False)
        inspect.return_value='learnplatform-e2e|true'
        ensure_e2e_container('learnplatform-e2e-backend')
