#!/usr/bin/env python3
"""Validate new non-merge Git commits against project conventions."""

from __future__ import annotations

import os
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


def parse_log_output(output: str) -> list[CommitRecord]:
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


def read_commits(revision: str) -> list[CommitRecord]:
    output = git(
        "log",
        "--no-merges",
        "--format=%H%x09%s%x09%b%x00",
        revision,
    )
    return parse_log_output(output)


def read_head_commit(head_sha: str) -> list[CommitRecord]:
    """读取单个 HEAD 提交，不遍历整个仓库历史。"""
    output = git(
        "log",
        "-1",
        "--no-merges",
        "--format=%H%x09%s%x09%b%x00",
        head_sha,
    )
    return parse_log_output(output)


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


def run_check(base_sha: str, head_sha: str, forced: bool = False) -> int:
    """校验新增非合并提交。

    forced 表示本次 push 被 GitHub 明确标记为 force push（授权历史重写）。
    历史重写后旧 BASE_SHA 可能不再可达：此时不再因无法读取旧基线而失败，
    只校验当前 HEAD 提交；无法可靠确定的新增范围不会被编造。
    """
    revision = commit_range(base_sha, head_sha)
    try:
        commits = read_commits(revision)
    except subprocess.CalledProcessError as error:
        if not forced:
            print(f"读取提交记录失败：{error}", file=sys.stderr)
            return 2
        print(
            "检测到明确的 force push 且旧 BASE_SHA 已不可达；"
            "本轮只校验当前 HEAD 提交，不检查整个仓库历史。",
            file=sys.stderr,
        )
        try:
            commits = read_head_commit(head_sha)
        except (subprocess.CalledProcessError, ValueError) as head_error:
            print(f"读取 HEAD 提交失败：{head_error}", file=sys.stderr)
            return 2
    except ValueError as error:
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


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "usage: check-commit-messages.py <base-sha> <head-sha>",
            file=sys.stderr,
        )
        return 2

    forced = os.environ.get("FORCED_PUSH", "").strip().lower() in ("1", "true", "yes")
    return run_check(sys.argv[1], sys.argv[2], forced=forced)


if __name__ == "__main__":
    raise SystemExit(main())
