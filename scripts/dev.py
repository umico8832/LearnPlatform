#!/usr/bin/env python3
"""Run local application processes against the project's Docker dependencies."""

from __future__ import annotations

import argparse
import errno
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
INFRA = ("mysql", "redis", "mailpit")
PUBLIC_ENV = ("VITE_TURNSTILE_SITE_KEY", "VITE_API_BASE_URL", "VITE_AI_TIMEOUT")


class DevError(RuntimeError):
    pass


def compose(*args: str, development: bool = True) -> list[str]:
    command = ["docker", "compose", "-f", "docker-compose.yml"]
    if development:
        command += ["-f", "docker-compose.dev.yml"]
    return [*command, *args]


def run(command: list[str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=capture)
    if result.returncode:
        # Compose diagnostics can contain interpolated configuration values.
        raise DevError("命令执行失败；请检查 Docker 是否启动、配置是否完整以及端口是否被占用。")
    return result


def config(*, development: bool = True) -> dict:
    result = run(compose("config", "--format", "json", development=development), capture=True)
    return json.loads(result.stdout)


def ensure_free(port: int, *, docker_port: bool = False) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("127.0.0.1", port))
        except OSError as error:
            if docker_port and error.errno == errno.EACCES:
                # Docker Desktop can publish privileged ports that this process cannot bind.
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=1):
                        pass
                except ConnectionRefusedError:
                    return
            raise DevError(f"端口 {port} 已被占用，请先停止对应服务；本入口不会结束其他进程。") from error


def running_container(service: str) -> str:
    return run(compose("ps", "-q", service), capture=True).stdout.strip()


def require_infra(settings: dict | None = None) -> None:
    for service in INFRA:
        container = running_container(service)
        if not container:
            raise DevError("基础服务未运行，请先执行 python3 scripts/dev.py infra-up。")
        state = json.loads(run(
            ["docker", "inspect", container, "--format", "{{json .State}}"], capture=True,
        ).stdout)
        if not state.get("Running") or state.get("Health", {}).get("Status", "healthy") != "healthy":
            raise DevError(f"{service} 尚未就绪，请执行 python3 scripts/dev.py status。")
        if settings is not None:
            bindings = json.loads(run(
                ["docker", "inspect", container, "--format", "{{json .NetworkSettings.Ports}}"], capture=True,
            ).stdout) or {}
            for mapping in settings["services"][service].get("ports", []):
                key = f"{mapping['target']}/{mapping.get('protocol', 'tcp')}"
                if not any(item["HostPort"] == str(mapping["published"])
                           and item["HostIp"] == mapping.get("host_ip", "0.0.0.0")
                           for item in (bindings.get(key) or [])):
                    raise DevError(f"{service} 端口配置尚未应用，请先执行 python3 scripts/dev.py infra-up。")


def check_docker_ports() -> None:
    services = config(development=False)["services"]
    for service in ("backend", "frontend"):
        container = running_container(service)
        bindings = {}
        if container:
            bindings = json.loads(run(
                ["docker", "inspect", container, "--format", "{{json .NetworkSettings.Ports}}"],
                capture=True,
            ).stdout) or {}
        owned_ports = {int(item["HostPort"]) for items in bindings.values() for item in (items or [])}
        for mapping in services[service].get("ports", []):
            port = int(mapping["published"])
            if port not in owned_ports:
                ensure_free(port, docker_port=True)


def process_env(service: str, settings: dict, inherited: dict[str, str]) -> dict[str, str]:
    if service == "backend":
        environment = inherited.copy()
        environment.update({key: str(value) for key, value in settings["services"]["backend"]["environment"].items()
                            if value is not None})
        return environment
    # Do not pass backend credentials inherited from an earlier shell export to Vite.
    environment = {key: value for key, value in inherited.items()
                   if key in ("PATH", "HOME", "TMPDIR", "TEMP", "TMP", "SystemRoot", "COMSPEC", "PATHEXT")
                   or key.startswith(("LC_", "LANG", "TERM"))}
    arguments = settings["services"]["frontend"]["build"]["args"]
    environment.update({key: str(arguments.get(key) or "") for key in PUBLIC_ENV})
    return environment


def launch(service: str) -> None:
    settings = config()
    port = {"backend": 8080, "frontend": 5173, "admin": 5174}[service]
    ensure_free(port)
    if service == "backend":
        require_infra(settings)
        executable = shutil.which("mvn")
        if executable:
            command = [executable, "spring-boot:run"]
        elif (ROOT / "backend/mvnw").is_file() and os.name != "nt":
            command = ["sh", "./mvnw", "spring-boot:run"]
        else:
            raise DevError("需要 Maven 3.8+（或仓库中的 Maven Wrapper）和 JDK 21。")
        directory = ROOT / "backend"
    else:
        executable = shutil.which("npm")
        if not executable:
            raise DevError("需要安装 Node.js 和 npm。")
        if not (ROOT / "frontend/node_modules").is_dir():
            raise DevError("请先在 frontend 目录执行 npm ci 安装依赖。")
        command = [executable, "run", "dev:admin" if service == "admin" else "dev",
                   "--", "--host", "127.0.0.1", "--strictPort"]
        directory = ROOT / "frontend"
        if not settings["services"]["frontend"]["build"]["args"].get("VITE_TURNSTILE_SITE_KEY"):
            print("提示：尚未配置 Turnstile 站点密钥，登录验证将不可用。", flush=True)
    print(f"启动 {service}，端口 {port}；使用 Ctrl+C 停止。", flush=True)
    environment = process_env(service, settings, dict(os.environ))
    os.chdir(directory)
    os.execvpe(command[0], command, environment)


def main() -> int:
    parser = argparse.ArgumentParser(description="本机前后端 + Docker 基础服务")
    parser.add_argument("command", choices=("infra-up", "infra-stop", "status", "backend", "frontend", "admin", "check-docker"))
    args = parser.parse_args()
    try:
        if args.command == "infra-up":
            if running_container("backend"):
                raise DevError("Docker 后端仍在运行，请先执行 docker compose stop frontend backend，再启动开发环境。")
            run(compose("up", "-d", "--no-deps", "--no-build", "--wait", "--wait-timeout", "180", *INFRA))
            require_infra()
            print("基础服务已就绪。分别在两个终端执行 dev.py backend 和 dev.py frontend。")
            run(compose("ps", *INFRA))
        elif args.command == "infra-stop":
            run(compose("stop", "--timeout", "60", *INFRA))
            print("基础服务已停止，容器和数据卷保留。前后端请在各自终端按 Ctrl+C。")
        elif args.command == "status":
            run(compose("ps", "-a", *INFRA))
        elif args.command == "check-docker":
            check_docker_ports()
        else:
            launch(args.command)
        return 0
    except (DevError, OSError, ValueError) as error:
        print(f"错误：{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
