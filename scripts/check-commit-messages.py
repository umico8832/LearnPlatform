#!/usr/bin/env python3
"""Validate new non-merge Git commits against project conventions."""

from __future__ import annotations

import re
import subprocess
import sys
from dataclasses import dataclass

ALLOWED_TYPES = (
    "feat",
    "fix",
    "docs",
    "style",
    "refactor",
    "perf",
    "test",
    "build",
    "ci",
    "security",
    "chore",
    "revert",
)
MAX_SUBJECT_LENGTH = 72
SUBJECT = re.compile(
    rf"^(?P<type>{'|'.join(ALLOWED_TYPES)})"
    r"(?:\((?P<scope>[a-z0-9]+(?:-[a-z0-9]+)*)\))?"
    r"(?P<breaking>!)?: (?P<description>.+)$"
)
CHINESE = re.compile(r"[\u4e00-\u9fff]")
BREAKING_FOOTER = re.compile(
    r"^BREAKING(?: |-)CHANGE: \S.*$",
    re.MULTILINE,
)
FORBIDDEN_ENDINGS = (".", "。", "!", "！", ";", "；")
ZERO_SHA = re.compile(r"^0+$")


@dataclass(frozen=True)
class CommitRecord:
    sha: str
    subject: str
    body: str


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout


def commit_range(base_sha: str, head_sha: str) -> str:
    if not base_sha or ZERO_SHA.fullmatch(base_sha):
        return head_sha
    return f"{base_sha}..{head_sha}"


def read_commits(revision: str) -> list[CommitRecord]:
    output = git(
        "log",
        "--no-merges",
        "--format=%H%x09%s%x09%b%x00",
        revision,
    )
    records: list[CommitRecord] = []
    for raw_record in output.split("\x00"):
        normalized = raw_record.strip("\r\n")
        if not normalized:
            continue
        try:
            sha, subject, body = normalized.split("\t", 2)
        except ValueError as error:
            raise ValueError("无法解析 git log 输出") from error
        records.append(CommitRecord(sha=sha, subject=subject, body=body.rstrip()))
    return records


def validate_commit(subject: str, body: str) -> list[str]:
    failures: list[str] = []
    if len(subject) > MAX_SUBJECT_LENGTH:
        failures.append(
            f"主题长度为 {len(subject)}，超过 {MAX_SUBJECT_LENGTH} 个字符"
        )

    match = SUBJECT.fullmatch(subject)
    if not match:
        failures.append(
            "主题格式应为 <type>(<可选小写 scope>)<可选 !>: <中文描述>"
        )
        return failures

    description = match.group("description")
    if not CHINESE.search(description):
        failures.append("主题描述必须包含中文")
    if description.endswith(FORBIDDEN_ENDINGS):
        failures.append("主题不能以句号、分号或感叹号结尾")

    has_marker = match.group("breaking") is not None
    has_footer = BREAKING_FOOTER.search(body) is not None
    if has_marker and not has_footer:
        failures.append("使用 ! 时必须提供 BREAKING CHANGE: footer")
    if has_footer and not has_marker:
        failures.append("提供 BREAKING CHANGE: footer 时必须在主题中使用 !")

    return failures


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "usage: check-commit-messages.py <base-sha> <head-sha>",
            file=sys.stderr,
        )
        return 2

    revision = commit_range(sys.argv[1], sys.argv[2])
    try:
        commits = read_commits(revision)
    except (subprocess.CalledProcessError, ValueError) as error:
        print(f"读取提交记录失败：{error}", file=sys.stderr)
        return 2

    failures: list[str] = []
    for commit in commits:
        commit_failures = validate_commit(commit.subject, commit.body)
        failures.extend(
            f"{commit.sha[:8]} {commit.subject}\n  - {failure}"
            for failure in commit_failures
        )

    if failures:
        print("以下新增 Commit Message 不符合项目规范：", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        print(
            "期望格式：<type>(<可选 scope>)<可选 !>: <中文简短描述>",
            file=sys.stderr,
        )
        return 1

    print(f"Commit Message 校验通过：{len(commits)} 个非合并提交。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
