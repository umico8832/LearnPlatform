#!/usr/bin/env python3
"""Build, run, verify, and clean up LearnPlatform Docker environments."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shlex
import socket
import subprocess
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence


ROOT = Path(__file__).resolve().parents[1]
FRONTEND_ROOT = ROOT / "frontend"
BASE_COMPOSE = ("docker", "compose", "-f", "docker-compose.yml")
E2E_COMPOSE = (*BASE_COMPOSE, "-f", "docker-compose.e2e.yml")
APP_SERVICES = ("backend", "frontend")
APP_IMAGE_TAGS = {
    "backend": "learnplatform-backend:latest",
    "frontend": "learnplatform-frontend:latest",
}
E2E_IMAGE_TAGS = {
    "backend": "learnplatform-e2e-backend:latest",
    "frontend": "learnplatform-e2e-frontend:latest",
}
HEALTH_TIMEOUT_SECONDS = 180


class LifecycleError(RuntimeError):
    """A lifecycle command failed and the requested environment is not ready."""


@dataclass(frozen=True)
class SourceMetadata:
    revision: str
    state: str
    fingerprint: str


@dataclass(frozen=True)
class ImageInfo:
    image_id: str
    labels: dict[str, str]


@dataclass(frozen=True)
class ContainerInfo:
    image_id: str
    health: str


def run_command(
    command: Sequence[str],
    *,
    cwd: Path = ROOT,
    env: Mapping[str, str] | None = None,
    check: bool = True,
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    print("+ " + shlex.join(command), flush=True)
    proc = subprocess.run(
        list(command),
        cwd=cwd,
        env=dict(env) if env is not None else None,
        text=True,
        capture_output=capture,
    )
    if check and proc.returncode != 0:
        detail = (proc.stderr or proc.stdout or "").strip()
        message = f"命令失败（{proc.returncode}）：{shlex.join(command)}"
        if detail:
            message += f"\n{detail}"
        raise LifecycleError(message)
    return proc


def compose_command(*args: str, e2e: bool = False) -> tuple[str, ...]:
    return (*(E2E_COMPOSE if e2e else BASE_COMPOSE), *args)


def require_docker() -> None:
    run_command(("docker", "version", "--format", "{{.Server.Version}}"), capture=True)


def git_revision() -> str:
    proc = run_command(("git", "rev-parse", "--verify", "HEAD"), capture=True)
    return proc.stdout.strip()


def source_paths(context: str) -> list[Path]:
    proc = run_command(
        (
            "git",
            "ls-files",
            "-z",
            "--cached",
            "--others",
            "--exclude-standard",
            "--",
            context,
        ),
        capture=True,
    )
    paths = [ROOT / item for item in proc.stdout.split("\0") if item]
    return sorted((path for path in paths if path.is_file()), key=lambda path: str(path))


def source_metadata(context: str, revision: str | None = None) -> SourceMetadata:
    resolved_revision = revision or git_revision()
    paths = source_paths(context)
    digest = hashlib.sha256()
    for path in paths:
        relative = path.relative_to(ROOT).as_posix().encode()
        digest.update(len(relative).to_bytes(4, "big"))
        digest.update(relative)
        content = path.read_bytes()
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)

    status = run_command(
        ("git", "status", "--porcelain", "--untracked-files=normal", "--", context),
        capture=True,
    )
    state = "dirty" if status.stdout.strip() else "clean"
    return SourceMetadata(resolved_revision, state, digest.hexdigest())


def lifecycle_environment() -> tuple[dict[str, str], dict[str, SourceMetadata]]:
    revision = git_revision()
    metadata = {
        "backend": source_metadata("backend", revision),
        "frontend": source_metadata("frontend", revision),
    }
    env = os.environ.copy()
    env.update(
        {
            "APP_REVISION": revision,
            "APP_BACKEND_WORKTREE_STATE": metadata["backend"].state,
            "APP_FRONTEND_WORKTREE_STATE": metadata["frontend"].state,
            "APP_BACKEND_SOURCE_FINGERPRINT": metadata["backend"].fingerprint,
            "APP_FRONTEND_SOURCE_FINGERPRINT": metadata["frontend"].fingerprint,
        }
    )
    return env, metadata


def inspect_image(tag: str) -> ImageInfo | None:
    proc = run_command(
        (
            "docker",
            "image",
            "inspect",
            tag,
            "--format",
            "{{.Id}}\t{{json .Config.Labels}}",
        ),
        check=False,
        capture=True,
    )
    if proc.returncode != 0 or not proc.stdout.strip():
        return None
    image_id, raw_labels = proc.stdout.strip().split("\t", 1)
    labels = json.loads(raw_labels) if raw_labels and raw_labels != "null" else {}
    return ImageInfo(image_id, labels)


def inspect_container(service: str, *, e2e: bool = False) -> ContainerInfo | None:
    proc = run_command(
        compose_command("ps", "-q", service, e2e=e2e),
        check=False,
        capture=True,
    )
    container_id = proc.stdout.strip()
    if proc.returncode != 0 or not container_id:
        return None
    inspected = run_command(
        ("docker", "inspect", container_id, "--format", "{{.Image}}\t{{json .State}}"),
        capture=True,
    )
    image_id, raw_state = inspected.stdout.strip().split("\t", 1)
    state = json.loads(raw_state)
    health = state.get("Health", {}).get("Status") or state.get("Status", "unknown")
    return ContainerInfo(image_id, health)


def image_matches_source(image: ImageInfo | None, metadata: SourceMetadata) -> bool:
    if image is None:
        return False
    return (
        image.labels.get("org.opencontainers.image.revision") == metadata.revision
        and image.labels.get("com.learnplatform.worktree-state") == metadata.state
        and image.labels.get("com.learnplatform.source-fingerprint") == metadata.fingerprint
    )


def app_status() -> int:
    require_docker()
    _, metadata = lifecycle_environment()
    failed = False
    for service in APP_SERVICES:
        image = inspect_image(APP_IMAGE_TAGS[service])
        container = inspect_container(service)
        current_source = image_matches_source(image, metadata[service])
        current_container = image is not None and container is not None and image.image_id == container.image_id
        healthy = container is not None and container.health == "healthy"
        failed = failed or not (current_source and current_container and healthy)
        print(
            f"{service}: source={'current' if current_source else 'stale'} "
            f"container={'current' if current_container else 'stale'} "
            f"health={container.health if container else 'missing'}"
        )
        if image:
            print(
                "  image="
                f"{image.image_id[:19]} revision="
                f"{image.labels.get('org.opencontainers.image.revision', 'unknown')[:12]} "
                f"worktree={image.labels.get('com.learnplatform.worktree-state', 'unknown')}"
            )
    return 1 if failed else 0


def image_id(tag: str) -> str:
    image = inspect_image(tag)
    if image is None:
        raise LifecycleError(f"构建后找不到镜像：{tag}")
    return image.image_id


def compose_container_id(service: str) -> str:
    proc = run_command(compose_command("ps", "-q", service), check=False, capture=True)
    return proc.stdout.strip() if proc.returncode == 0 else ""


def pin_rollback_images(old_images: Mapping[str, str]) -> dict[str, str]:
    rollback_tags: dict[str, str] = {}
    suffix = f"rollback-{os.getpid()}"
    for service in APP_SERVICES:
        old_id = old_images.get(service)
        if not old_id:
            continue
        repository = APP_IMAGE_TAGS[service].split(":", 1)[0]
        rollback_tag = f"{repository}:{suffix}"
        tagged = run_command(("docker", "tag", old_id, rollback_tag), check=False)
        if tagged.returncode != 0:
            container_id = compose_container_id(service)
            committed = (
                run_command(
                    ("docker", "commit", container_id, rollback_tag),
                    check=False,
                )
                if container_id
                else None
            )
            if committed is None or committed.returncode != 0:
                print(
                    f"警告：{service} 的历史镜像内容不可用，本次无法建立回滚引用；"
                    "旧容器会保留到新镜像构建完成。",
                    file=sys.stderr,
                )
                continue
        rollback_tags[service] = rollback_tag
    return rollback_tags


def remove_rollback_tags(tags: Mapping[str, str]) -> None:
    for tag in tags.values():
        run_command(("docker", "image", "rm", tag), check=False)


def start_app_stack(env: Mapping[str, str]) -> None:
    run_command(
        compose_command(
            "up",
            "-d",
            "--no-build",
            "--wait",
            "--wait-timeout",
            str(HEALTH_TIMEOUT_SECONDS),
            "mysql",
            "redis",
            "mailpit",
            "loki",
        ),
        env=env,
    )
    for service in APP_SERVICES:
        run_command(
            compose_command(
                "up",
                "-d",
                "--no-deps",
                "--no-build",
                "--force-recreate",
                "--wait",
                "--wait-timeout",
                str(HEALTH_TIMEOUT_SECONDS),
                service,
            ),
            env=env,
        )
    run_command(
        compose_command(
            "up",
            "-d",
            "--no-build",
            "--wait",
            "--wait-timeout",
            str(HEALTH_TIMEOUT_SECONDS),
            "prometheus",
            "grafana",
        ),
        env=env,
    )


def mapped_port(service: str, container_port: int, env: Mapping[str, str]) -> int:
    proc = run_command(
        compose_command("port", service, str(container_port)),
        env=env,
        capture=True,
    )
    endpoint = proc.stdout.strip().splitlines()[0]
    try:
        return int(endpoint.rsplit(":", 1)[1])
    except (IndexError, ValueError) as error:
        raise LifecycleError(f"无法解析 {service} 映射端口：{endpoint}") from error


def require_http(url: str) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "LearnPlatform-Docker-Lifecycle"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            if response.status < 200 or response.status >= 400:
                raise LifecycleError(f"HTTP 验证失败：{url} 返回 {response.status}")
    except (urllib.error.URLError, TimeoutError) as error:
        raise LifecycleError(f"HTTP 验证失败：{url}（{error}）") from error


def verify_app(env: Mapping[str, str], expected_images: Mapping[str, str]) -> None:
    for service in APP_SERVICES:
        container = inspect_container(service)
        if container is None:
            raise LifecycleError(f"{service} 容器不存在")
        if container.health != "healthy":
            raise LifecycleError(f"{service} 容器状态不是 healthy：{container.health}")
        if container.image_id != expected_images[service]:
            raise LifecycleError(f"{service} 容器没有使用刚构建的镜像")

    backend_port = mapped_port("backend", 8080, env)
    frontend_port = mapped_port("frontend", 80, env)
    require_http(f"http://127.0.0.1:{backend_port}/api/public/health")
    require_http(f"http://127.0.0.1:{frontend_port}/api/public/health")
    require_http(f"http://127.0.0.1:{frontend_port}/")
    print(f"学习端：http://localhost:{frontend_port}")
    print(f"后端：http://localhost:{backend_port}")


def restore_image_tags(rollback_tags: Mapping[str, str]) -> None:
    for service, rollback_tag in rollback_tags.items():
        run_command(("docker", "tag", rollback_tag, APP_IMAGE_TAGS[service]), check=False)


def rollback_app(rollback_tags: Mapping[str, str], env: Mapping[str, str]) -> None:
    if not rollback_tags:
        return
    print("新环境验证失败，正在恢复替换前的应用镜像。", file=sys.stderr)
    restore_image_tags(rollback_tags)
    for service in APP_SERVICES:
        if service in rollback_tags:
            run_command(
                compose_command(
                    "up",
                    "-d",
                    "--no-deps",
                    "--no-build",
                    "--force-recreate",
                    "--wait",
                    "--wait-timeout",
                    str(HEALTH_TIMEOUT_SECONDS),
                    service,
                ),
                env=env,
                check=False,
            )
    remove_rollback_tags(rollback_tags)


def reclaim() -> None:
    run_command((sys.executable, str(ROOT / "scripts" / "docker-disk.py"), "reclaim"))


def app_up() -> int:
    require_docker()
    run_command((sys.executable, str(ROOT / "scripts" / "dev.py"), "check-docker"))
    env, metadata = lifecycle_environment()
    run_command(compose_command("config", "--quiet"), env=env)
    old_images = {
        service: container.image_id
        for service in APP_SERVICES
        if (container := inspect_container(service)) is not None
    }
    rollback_tags = pin_rollback_images(old_images)
    try:
        run_command(compose_command("build", "backend", "frontend"), env=env)
    except Exception:
        restore_image_tags(rollback_tags)
        remove_rollback_tags(rollback_tags)
        raise
    new_images = {service: image_id(APP_IMAGE_TAGS[service]) for service in APP_SERVICES}
    try:
        start_app_stack(env)
        verify_app(env, new_images)
        for service in APP_SERVICES:
            if not image_matches_source(inspect_image(APP_IMAGE_TAGS[service]), metadata[service]):
                raise LifecycleError(f"{service} 镜像缺少当前源码身份标签")
    except Exception:
        rollback_app(rollback_tags, env)
        raise

    remove_rollback_tags(rollback_tags)
    reclaim()
    return 0


def port_available(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        try:
            sock.bind(("127.0.0.1", port))
        except OSError:
            return False
    return True


def available_port(preferred: int) -> int:
    if port_available(preferred):
        return preferred
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def start_e2e(env: Mapping[str, str]) -> None:
    run_command(compose_command("config", "--quiet", e2e=True), env=env)
    run_command(
        compose_command(
            "up",
            "-d",
            "--build",
            "--force-recreate",
            "--wait",
            "--wait-timeout",
            str(HEALTH_TIMEOUT_SECONDS),
            "backend",
            "frontend",
            e2e=True,
        ),
        env=env,
    )


def run_playwright(env: Mapping[str, str], playwright_args: Sequence[str]) -> int:
    command = ("npm", "run", "test:e2e:playwright", "--", *playwright_args)
    return run_command(command, cwd=FRONTEND_ROOT, env=env, check=False).returncode


def show_e2e_failure_logs(env: Mapping[str, str]) -> None:
    run_command(
        compose_command("logs", "--no-color", "--tail", "200", "backend", "frontend", e2e=True),
        env=env,
        check=False,
    )


def stop_e2e(env: Mapping[str, str]) -> int:
    down_code = run_command(
        compose_command("down", "-v", e2e=True),
        env=env,
        check=False,
    ).returncode
    image_code = 0
    for tag in E2E_IMAGE_TAGS.values():
        proc = run_command(("docker", "image", "rm", tag), check=False)
        if proc.returncode != 0:
            image_code = proc.returncode
    return down_code or image_code


def e2e(playwright_args: Sequence[str]) -> int:
    require_docker()
    env, _ = lifecycle_environment()
    preferred_port = int(env.get("E2E_FRONTEND_HOST_PORT", "18000"))
    selected_port = available_port(preferred_port)
    env["FRONTEND_HOST_PORT"] = str(selected_port)
    env["E2E_BASE_URL"] = f"http://127.0.0.1:{selected_port}"
    print(f"E2E 地址：{env['E2E_BASE_URL']}")

    test_code = 1
    cleanup_code = 0
    lifecycle_error: Exception | None = None
    try:
        start_e2e(env)
        test_code = run_playwright(env, playwright_args)
        if test_code != 0:
            show_e2e_failure_logs(env)
    except Exception as error:
        lifecycle_error = error
    finally:
        cleanup_code = stop_e2e(env)
        try:
            reclaim()
        except Exception as error:
            if lifecycle_error is None:
                lifecycle_error = error
    if lifecycle_error is not None:
        raise lifecycle_error
    return test_code or cleanup_code


def main() -> int:
    parser = argparse.ArgumentParser(description="LearnPlatform Docker 生命周期")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("app-status", help="检查日常容器、镜像和当前源码是否一致")
    subparsers.add_parser("app-up", help="构建当前源码、替换日常应用并安全回收旧镜像")
    e2e_parser = subparsers.add_parser("e2e", help="构建、运行并清理隔离浏览器 E2E 环境")
    e2e_parser.add_argument("playwright_args", nargs=argparse.REMAINDER)
    args = parser.parse_args()

    try:
        if args.command == "app-status":
            return app_status()
        if args.command == "app-up":
            return app_up()
        playwright_args = list(args.playwright_args)
        if playwright_args[:1] == ["--"]:
            playwright_args = playwright_args[1:]
        return e2e(playwright_args)
    except (LifecycleError, ValueError) as error:
        print(f"错误：{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
