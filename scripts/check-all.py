#!/usr/bin/env python3
"""Run the same checks locally and in each CI job."""

from __future__ import annotations

import argparse
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence


ROOT = Path(__file__).resolve().parents[1]
JOBS = ("docs", "backend", "backend-integration", "frontend", "docker", "e2e")
RECORD_NAME = "check-all-passed"


class CheckError(RuntimeError):
    """A check failed before the requested gate completed."""


@dataclass(frozen=True)
class Step:
    name: str
    command: tuple[str, ...]
    directory: str = "."


def maven() -> tuple[str, ...]:
    executable = shutil.which("mvn")
    return (executable,) if executable else ("sh", "./mvnw")


def job_steps(job: str, env: Mapping[str, str], *, skip_commits: bool = False) -> list[Step]:
    if job == "docs":
        steps = [Step("tools", (sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_*.py"))]
        if not skip_commits:
            base = env.get("BASE_SHA", "origin/main")
            head = env.get("HEAD_SHA", "HEAD")
            steps.append(Step("commits", (sys.executable, "scripts/check-commit-messages.py", base, head)))
        steps.extend(Step(name, (sys.executable, f"scripts/check-{script}.py")) for name, script in (
            ("layout", "repository-layout"), ("structure", "code-structure"), ("documentation", "docs")))
        return steps
    if job == "backend":
        return [Step("verify", (*maven(), "clean", "verify", "-B", *backend_test_options(env)), "backend")]
    if job == "backend-integration":
        return [Step("integration", (*maven(), "test", "-B", "-DexcludedGroups=", "-Dgroups=integration",
                                     *backend_test_options(env)), "backend")]
    if job == "frontend":
        return [Step(name, command, "frontend") for name, command in (
            ("dependencies", ("npm", "ci")),
            ("audit", ("npm", "audit", "--audit-level=high")),
            ("coverage", ("npm", "run", "coverage")),
            ("lint", ("npm", "run", "lint")),
            ("build", ("npm", "run", "build")),
        )]
    if job == "docker":
        return [Step(service, ("docker", "build", "-t", f"learnplatform-{service}", f"./{service}"))
                for service in ("backend", "frontend")]
    if job == "e2e":
        return [Step("dependencies", ("npm", "ci"), "frontend"),
                Step("tests", ("npm", "run", "test:e2e"), "frontend")]
    raise CheckError(f"未知 job：{job}")


def backend_test_options(env: Mapping[str, str]) -> tuple[str, ...]:
    zone = env.get("BACKEND_TEST_TIMEZONE", "UTC")
    order = env.get("BACKEND_TEST_ORDER", "alphabetical")
    options = (f"-Duser.timezone={zone}", f"-Dsurefire.runOrder={order}")
    if order == "random":
        seed = env.get("BACKEND_TEST_SEED", "20261018")
        options += (f"-Dsurefire.runOrder.random.seed={seed}",
                    "-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random",
                    f"-Djunit.jupiter.execution.order.random.seed={seed}")
    return options


def git(*args: str) -> str:
    return subprocess.run(("git", *args), cwd=ROOT, check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()


def record_path() -> Path:
    return (ROOT / git("rev-parse", "--git-common-dir")).resolve() / RECORD_NAME


def clean_worktree() -> bool:
    return not git("status", "--porcelain", "--untracked-files=all")


def write_record(path: Path, head: str) -> None:
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                     prefix=f"{RECORD_NAME}-", delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(head + "\n")
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def run_step(job: str, step: Step, env: Mapping[str, str]) -> None:
    label = f"{job}/{step.name}"
    print(f"[{label}] + {shlex.join(step.command)}", flush=True)
    started = time.monotonic()
    environment = {**env, "CI": "true"}
    if job.startswith("backend"):
        environment["TZ"] = env.get("BACKEND_TEST_TIMEZONE", "UTC")
    if job == "e2e":
        environment.update(DB_PASSWORD="root123", JWT_SECRET="ci-e2e-jwt-secret-key-for-testing-only",
                           COMPOSE_DISABLE_ENV_FILE="1")
    try:
        result = subprocess.run(step.command, cwd=ROOT / step.directory, env=environment)
    except OSError as error:
        raise CheckError(f"失败：{label}：{error}") from error
    if result.returncode:
        raise CheckError(f"失败：{label}（退出码 {result.returncode}）")
    print(f"[{label}] 通过（{time.monotonic() - started:.1f}s）", flush=True)


def run_checks(jobs: Sequence[str], env: Mapping[str, str], *, step_name: str | None = None,
               skip_commits: bool = False) -> None:
    full = set(jobs) == set(JOBS) and step_name is None and not skip_commits
    head = git("rev-parse", "HEAD") if full else None
    initially_clean = clean_worktree() if full else False
    record = record_path() if full else None
    if record is not None:
        record.unlink(missing_ok=True)
    for job in jobs:
        steps = job_steps(job, env, skip_commits=skip_commits)
        if step_name is not None:
            steps = [step for step in steps if step.name == step_name]
            if not steps:
                raise CheckError(f"{job} 没有步骤 {step_name}")
        for step in steps:
            run_step(job, step, env)
    if full:
        if initially_clean and clean_worktree() and git("rev-parse", "HEAD") == head:
            write_record(record, head)
            print(f"已记录完整检查通过的提交：{head}（{record}）", flush=True)
        else:
            print("完整检查通过；工作区有未提交改动或 HEAD 变化，未写记录。请提交后重新运行完整检查。", flush=True)
    else:
        print("部分检查通过；未写完整检查记录。", flush=True)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jobs", nargs="*", help=f"默认全部；可选：{', '.join(JOBS)}")
    parser.add_argument("--step", help="只执行一个 job 的某一步（供 CI 在 E2E 依赖安装后准备浏览器）")
    parser.add_argument("--skip-commit-messages", action="store_true", help="保留 CI 对 Dependabot PR 的豁免；不写通过记录")
    parser.add_argument("--backend-timezone", choices=("UTC", "America/New_York"), default="UTC",
                        help="后端测试的机器时区，默认 UTC，与业务时区不同")
    parser.add_argument("--backend-order", choices=("alphabetical", "reversealphabetical", "random"),
                        default="alphabetical", help="后端测试类顺序；random 同时随机方法顺序")
    parser.add_argument("--backend-seed", type=int, default=20261018, help="随机类与方法顺序的复现种子")
    args = parser.parse_args(argv)
    jobs = args.jobs or list(JOBS)
    if any(job not in JOBS for job in jobs) or len(jobs) != len(set(jobs)):
        parser.error(f"job 必须不重复且属于：{', '.join(JOBS)}")
    if args.step and len(jobs) != 1:
        parser.error("--step 必须且只能指定一个 job")
    started = time.monotonic()
    try:
        environment = {**os.environ, "BACKEND_TEST_TIMEZONE": args.backend_timezone,
                       "BACKEND_TEST_ORDER": args.backend_order, "BACKEND_TEST_SEED": str(args.backend_seed)}
        run_checks(jobs, environment, step_name=args.step, skip_commits=args.skip_commit_messages)
    except (CheckError, OSError, subprocess.CalledProcessError) as error:
        print(f"check-all: {error}", file=sys.stderr, flush=True)
        return 1
    except KeyboardInterrupt:
        print("check-all: 检查已中断，未写通过记录。", file=sys.stderr, flush=True)
        return 130
    finally:
        print(f"检查总耗时：{time.monotonic() - started:.1f}s", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
