#!/usr/bin/env python3
"""Validate tracked repository paths before build tools silently ignore them."""

from __future__ import annotations

import subprocess
import sys
from pathlib import PurePosixPath


FORBIDDEN_PATH_CHARACTERS = frozenset('<>:"|?*')
FORBIDDEN_COMPONENTS = frozenset(
    {
        ".pytest_cache",
        ".venv",
        "__pycache__",
        "dist",
        "node_modules",
        "playwright-report",
        "target",
        "test-results",
    }
)
FORBIDDEN_FILENAMES = frozenset({".DS_Store", "Thumbs.db"})


def git_paths(*arguments: str) -> set[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z", *arguments],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return {path for path in result.stdout.split("\0") if path}


def tracked_paths() -> list[str]:
    tracked = git_paths("--cached")
    deleted = git_paths("--deleted")
    return sorted(tracked - deleted)


def validate_paths(paths: list[str]) -> list[str]:
    errors: list[str] = []
    for raw_path in sorted(paths):
        path = PurePosixPath(raw_path)
        for component in path.parts:
            illegal = sorted(FORBIDDEN_PATH_CHARACTERS.intersection(component))
            if illegal:
                errors.append(
                    f"{raw_path}: path component {component!r} contains forbidden "
                    f"character(s) {''.join(illegal)!r}"
                )
            if component in FORBIDDEN_COMPONENTS:
                errors.append(
                    f"{raw_path}: generated directory {component!r} must not be tracked"
                )
            if component in FORBIDDEN_FILENAMES:
                errors.append(f"{raw_path}: system file must not be tracked")
            if component.endswith((" ", ".")):
                errors.append(
                    f"{raw_path}: path component {component!r} must not end with space or dot"
                )
    return errors


def main() -> int:
    try:
        paths = tracked_paths()
    except subprocess.CalledProcessError as error:
        print(f"Failed to read tracked repository paths: {error}", file=sys.stderr)
        return 2

    errors = validate_paths(paths)
    if errors:
        print("Repository layout validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Repository layout validation passed: {len(paths)} tracked paths checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
