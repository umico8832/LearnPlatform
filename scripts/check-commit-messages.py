#!/usr/bin/env python3
"""Validate new non-merge Git commit subjects against project conventions."""

from __future__ import annotations

import re
import subprocess
import sys

SUBJECT = re.compile(
    r"^(feat|fix|docs|style|refactor|perf|test|chore|ci|security)"
    r"(?:\([^()\s]+\))?: (?=.*[\u4e00-\u9fff]).+$"
)
ZERO_SHA = re.compile(r"^0+$")


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout.strip()


def commit_range(base_sha: str, head_sha: str) -> str:
    if not base_sha or ZERO_SHA.fullmatch(base_sha):
        return head_sha
    return f"{base_sha}..{head_sha}"


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "usage: check-commit-messages.py <base-sha> <head-sha>",
            file=sys.stderr,
        )
        return 2

    revision = commit_range(sys.argv[1], sys.argv[2])
    output = git("log", "--no-merges", "--format=%H%x09%s", revision)
    failures: list[str] = []

    for line in output.splitlines():
        commit_sha, subject = line.split("\t", 1)
        if not SUBJECT.fullmatch(subject):
            failures.append(f"{commit_sha[:8]} {subject}")

    if failures:
        print("以下新增 commit message 不符合项目规范：", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        print(
            "期望格式：<type>(<可选作用域>): <中文简短描述>",
            file=sys.stderr,
        )
        return 1

    count = len(output.splitlines()) if output else 0
    print(f"Commit message 校验通过：{count} 个非合并提交。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
