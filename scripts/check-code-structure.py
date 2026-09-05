#!/usr/bin/env python3
"""Prevent oversized Spring services and Vue single-file components from returning."""

from __future__ import annotations

import re
import sys
from pathlib import Path


SERVICE_MAX_LINES = 400
SERVICE_MAX_DIRECT_DEPENDENCIES = 8
VUE_MAX_LINES = 699
VUE_SCRIPT_MAX_LINES = 300

SERVICE_ANNOTATION = re.compile(r"(?m)^\s*@Service(?:\s*\(|\s*$)")
DIRECT_DEPENDENCY = re.compile(r"(?m)^\s*private\s+final\s+(?!static\b)")
VUE_SCRIPT_BLOCK = re.compile(r"<script\b[^>]*>(.*?)</script>", re.IGNORECASE | re.DOTALL)


def count_lines(content: str) -> int:
    return len(content.splitlines())


def validate_service(path: Path, content: str) -> list[str]:
    if not SERVICE_ANNOTATION.search(content):
        return []

    errors: list[str] = []
    line_count = count_lines(content)
    if line_count > SERVICE_MAX_LINES:
        errors.append(
            f"{path.as_posix()}: Spring Service has {line_count} lines "
            f"(maximum {SERVICE_MAX_LINES})"
        )

    dependency_count = len(DIRECT_DEPENDENCY.findall(content))
    if dependency_count > SERVICE_MAX_DIRECT_DEPENDENCIES:
        errors.append(
            f"{path.as_posix()}: Spring Service has {dependency_count} direct dependencies "
            f"(maximum {SERVICE_MAX_DIRECT_DEPENDENCIES})"
        )
    return errors


def validate_vue(path: Path, content: str) -> list[str]:
    errors: list[str] = []
    line_count = count_lines(content)
    if line_count > VUE_MAX_LINES:
        errors.append(
            f"{path.as_posix()}: Vue SFC has {line_count} lines "
            f"(maximum {VUE_MAX_LINES})"
        )

    script_match = VUE_SCRIPT_BLOCK.search(content)
    if script_match:
        script_line_count = count_lines(script_match.group(1))
        if script_line_count > VUE_SCRIPT_MAX_LINES:
            errors.append(
                f"{path.as_posix()}: Vue script block has {script_line_count} lines "
                f"(maximum {VUE_SCRIPT_MAX_LINES})"
            )
    return errors


def validate_repository(root: Path) -> list[str]:
    errors: list[str] = []
    service_root = root / "backend/src/main/java/com/learnplatform/service"
    frontend_root = root / "frontend/src"

    for path in sorted(service_root.rglob("*.java")):
        errors.extend(validate_service(path.relative_to(root), path.read_text(encoding="utf-8")))
    for path in sorted(frontend_root.rglob("*.vue")):
        errors.extend(validate_vue(path.relative_to(root), path.read_text(encoding="utf-8")))
    return errors


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    errors = validate_repository(root)
    if errors:
        print("Code structure validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Code structure validation passed: "
        f"Spring Service <= {SERVICE_MAX_LINES} lines and <= "
        f"{SERVICE_MAX_DIRECT_DEPENDENCIES} direct dependencies; "
        f"Vue SFC <= {VUE_MAX_LINES} lines and script block <= "
        f"{VUE_SCRIPT_MAX_LINES} lines."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
