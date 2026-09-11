from __future__ import annotations

import importlib.util
import io
import json
from pathlib import Path
import socket
import subprocess
import unittest
from contextlib import redirect_stderr
from unittest.mock import Mock, patch


SPEC = importlib.util.spec_from_file_location("dev", Path(__file__).resolve().parents[1] / "dev.py")
dev = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dev)


class DevelopmentTest(unittest.TestCase):
    def settings(self):
        return {"services": {
            "backend": {"environment": {"DB_URL": "jdbc:mysql://127.0.0.1:13306/test", "JWT_SECRET": "example", "SERVER_PORT": "8080"}},
            "frontend": {"build": {"args": {"VITE_TURNSTILE_SITE_KEY": "public-example", "VITE_API_BASE_URL": "/api"}}},
        }}

    def test_frontend_only_receives_allowed_public_configuration(self):
        environment = dev.process_env("frontend", self.settings(), {
            "PATH": "/bin", "HOME": "/example", "JWT_SECRET": "private-example",
            "VITE_SECRET_KEY": "must-not-be-exposed", "NODE_OPTIONS": "injected",
        })
        self.assertEqual("public-example", environment["VITE_TURNSTILE_SITE_KEY"])
        self.assertEqual("/bin", environment["PATH"])
        self.assertNotIn("JWT_SECRET", environment)
        self.assertNotIn("VITE_SECRET_KEY", environment)
        self.assertNotIn("NODE_OPTIONS", environment)

    def test_backend_resolved_configuration_overrides_stale_shell_values(self):
        environment = dev.process_env("backend", self.settings(), {"DB_URL": "old", "SERVER_PORT": "18080"})
        self.assertIn("13306", environment["DB_URL"])
        self.assertEqual("8080", environment["SERVER_PORT"])

    def test_occupied_port_fails_without_killing_owner(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen()
            with self.assertRaisesRegex(dev.DevError, "已被占用"):
                dev.ensure_free(server.getsockname()[1])
            self.assertGreater(server.fileno(), 0)

    @patch.object(dev.socket, "create_connection", side_effect=ConnectionRefusedError)
    @patch.object(dev.socket, "socket")
    def test_docker_privileged_port_is_not_mistaken_for_occupied_port(self, socket_factory, connection):
        socket_factory.return_value.__enter__.return_value.bind.side_effect = PermissionError(13, "denied")
        dev.ensure_free(80, docker_port=True)
        with self.assertRaises(dev.DevError):
            dev.ensure_free(80)

    @patch.object(dev.subprocess, "run")
    def test_configuration_failure_does_not_leak_compose_output(self, execute):
        execute.return_value = subprocess.CompletedProcess([], 1, "secret-output", "secret-error")
        with self.assertRaises(dev.DevError) as raised:
            dev.config()
        self.assertNotIn("secret", str(raised.exception))
        self.assertTrue(execute.call_args.kwargs["capture_output"])

    @patch.object(dev, "running_container", return_value="")
    def test_backend_requires_running_dependencies(self, container):
        with self.assertRaisesRegex(dev.DevError, "infra-up"):
            dev.require_infra()

    @patch.object(dev, "run")
    @patch.object(dev, "running_container", return_value="container")
    def test_unhealthy_dependency_is_rejected(self, container, execute):
        execute.return_value.stdout = json.dumps({"Running": True, "Health": {"Status": "unhealthy"}})
        with self.assertRaisesRegex(dev.DevError, "尚未就绪"):
            dev.require_infra()

    @patch.object(dev, "run")
    @patch.object(dev.sys, "argv", ["dev.py", "infra-stop"])
    def test_stop_preserves_volumes_and_targets_only_dependencies(self, execute):
        self.assertEqual(0, dev.main())
        execute.assert_called_once_with(dev.compose("stop", "--timeout", "60", "mysql", "redis", "mailpit"))

    @patch.object(dev, "run")
    @patch.object(dev, "running_container", return_value="backend-container")
    @patch.object(dev.sys, "argv", ["dev.py", "infra-up"])
    def test_infra_start_refuses_to_reconfigure_running_docker_backend(self, container, execute):
        with redirect_stderr(io.StringIO()):
            self.assertEqual(1, dev.main())
        execute.assert_not_called()

    @patch.object(dev, "require_infra")
    @patch.object(dev, "run")
    @patch.object(dev, "running_container", return_value="")
    @patch.object(dev.sys, "argv", ["dev.py", "infra-up"])
    def test_start_does_not_build_or_start_applications(self, container, execute, ready):
        self.assertEqual(0, dev.main())
        command = execute.call_args_list[0].args[0]
        self.assertEqual(["mysql", "redis", "mailpit"], command[-3:])
        self.assertIn("--no-build", command)
        self.assertIn("--no-deps", command)

    @patch.object(dev, "ensure_free")
    @patch.object(dev, "running_container", return_value="")
    @patch.object(dev, "config")
    def test_docker_switch_checks_published_not_internal_ports(self, settings, container, available):
        settings.return_value = {"services": {
            "backend": {"ports": [{"published": "18080", "target": 8080}]},
            "frontend": {"ports": [{"published": "18000", "target": 80}]},
        }}
        dev.check_docker_ports()
        self.assertEqual([18080, 18000], [call.args[0] for call in available.call_args_list])

    @patch.object(dev, "run")
    @patch.object(dev, "running_container", return_value="container")
    def test_healthy_container_with_old_port_mapping_is_rejected(self, container, execute):
        execute.side_effect = [
            Mock(stdout=json.dumps({"Running": True, "Health": {"Status": "healthy"}})),
            Mock(stdout=json.dumps({"3306/tcp": None})),
        ]
        settings = {"services": {"mysql": {"ports": [{"target": 3306, "published": "13306", "host_ip": "127.0.0.1"}]}}}
        with self.assertRaisesRegex(dev.DevError, "端口配置尚未应用"):
            dev.require_infra(settings)

    @patch.object(dev, "ensure_free")
    @patch.object(dev, "run")
    @patch.object(dev, "running_container", return_value="container")
    @patch.object(dev, "config")
    def test_docker_switch_allows_ports_owned_by_existing_app_containers(self, settings, container, execute, available):
        settings.return_value = {"services": {
            "backend": {"ports": [{"published": "8080", "target": 8080}]},
            "frontend": {"ports": [{"published": "80", "target": 80}]},
        }}
        execute.side_effect = [
            Mock(stdout=json.dumps({"8080/tcp": [{"HostPort": "8080"}]})),
            Mock(stdout=json.dumps({"80/tcp": [{"HostPort": "80"}]})),
        ]
        dev.check_docker_ports()
        available.assert_not_called()

    @patch.object(dev.os, "execvpe")
    @patch.object(dev.os, "chdir")
    @patch.object(dev.shutil, "which", return_value="/example/mvn")
    @patch.object(dev, "require_infra")
    @patch.object(dev, "ensure_free")
    @patch.object(dev, "config")
    def test_backend_launches_foreground_with_local_configuration(self, settings, available, ready, executable, chdir, launch):
        settings.return_value = self.settings()
        dev.launch("backend")
        ready.assert_called_once_with(settings.return_value)
        available.assert_called_once_with(8080)
        self.assertEqual(["/example/mvn", "spring-boot:run"], launch.call_args.args[1])
        self.assertEqual("8080", launch.call_args.args[2]["SERVER_PORT"])


if __name__ == "__main__":
    unittest.main()
