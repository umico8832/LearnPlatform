#!/usr/bin/env python3
"""Validate LearnPlatform documentation links, navigation, and project Skills."""

from __future__ import annotations

import re
import sys
from collections import deque
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
DOCS_ROOT = ROOT / "docs"
PROJECT_SKILLS_ROOT = ROOT / "skills"

MARKDOWN_LINK = re.compile(r"!?\[[^\]]*]\(([^)]+)\)")
FRONTMATTER_FIELD = re.compile(r"^([a-zA-Z0-9_-]+):\s*(.+?)\s*$")

OLD_PATHS = (
    "docs/PRD.md",
    "docs/ROADMAP.md",
    "docs/FUTURE.md",
    "docs/AI_LEARNING_PLATFORM_STRATEGY.md",
    "docs/ARCHITECTURE.md",
    "docs/API_DESIGN.md",
    "docs/DB_DESIGN.md",
    "docs/AI_AGENT_DEVELOPMENT_WORKFLOW.md",
    "docs/TESTING.md",
    "docs/ENGINEERING_RULES.md",
    "docs/AGENT_GIT_RULES.md",
    "docs/AGENT_REVIEW_CHECKLIST.md",
    "docs/HANDOFF.md",
    "docs/CHANGELOG_AGENT.md",
    "docs/DEMO.md",
    "docs/RESUME.md",
    "docs/demo-screenshots",
    "skills/skill-creator",
)


def markdown_files() -> list[Path]:
    files = [ROOT / "README.md", ROOT / "AGENTS.md"]
    files.extend(sorted(DOCS_ROOT.rglob("*.md")))
    files.extend(sorted(PROJECT_SKILLS_ROOT.rglob("*.md")))
    pull_request_template = ROOT / ".github" / "PULL_REQUEST_TEMPLATE.md"
    if pull_request_template.exists():
        files.append(pull_request_template)
    return [path for path in files if path.exists()]


def normalize_link(raw_link: str) -> str:
    link = raw_link.strip()
    if link.startswith("<") and ">" in link:
        link = link[1 : link.index(">")]
    elif " " in link:
        link = link.split(" ", 1)[0]
    return unquote(link.split("#", 1)[0])


def local_link_targets(path: Path) -> list[Path]:
    text = path.read_text(encoding="utf-8")
    targets: list[Path] = []
    for match in MARKDOWN_LINK.finditer(text):
        link = normalize_link(match.group(1))
        if not link or link.startswith(
            ("http://", "https://", "mailto:", "data:", "app://")
        ):
            continue
        target = (path.parent / link).resolve()
        targets.append(target)
    return targets


def check_links(files: list[Path], errors: list[str]) -> None:
    for path in files:
        for target in local_link_targets(path):
            try:
                target.relative_to(ROOT)
            except ValueError:
                errors.append(
                    f"{path.relative_to(ROOT)}: link escapes repository: {target}"
                )
                continue
            if not target.exists():
                errors.append(
                    f"{path.relative_to(ROOT)}: missing link target "
                    f"{target.relative_to(ROOT)}"
                )


def check_docs_navigation(errors: list[str]) -> None:
    index = DOCS_ROOT / "index.md"
    reachable: set[Path] = set()
    queue: deque[Path] = deque([index.resolve()])
    while queue:
        path = queue.popleft()
        if path in reachable or not path.exists() or path.suffix != ".md":
            continue
        reachable.add(path)
        for target in local_link_targets(path):
            if target.suffix == ".md" and target.is_relative_to(DOCS_ROOT):
                queue.append(target)

    for path in sorted(DOCS_ROOT.rglob("*.md")):
        if path.resolve() not in reachable:
            errors.append(
                f"{path.relative_to(ROOT)}: not reachable from docs/index.md"
            )


def check_old_paths(files: list[Path], errors: list[str]) -> None:
    changelog_root = DOCS_ROOT / "project" / "changelog"
    for path in files:
        if path.is_relative_to(changelog_root):
            continue
        text = path.read_text(encoding="utf-8")
        for old_path in OLD_PATHS:
            if old_path in text:
                errors.append(
                    f"{path.relative_to(ROOT)}: contains legacy path {old_path}"
                )


def parse_frontmatter(path: Path) -> dict[str, str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0].strip() != "---":
        return {}
    fields: dict[str, str] = {}
    for line in lines[1:]:
        if line.strip() == "---":
            return fields
        match = FRONTMATTER_FIELD.match(line)
        if match:
            fields[match.group(1)] = match.group(2).strip("\"'")
    return {}


def check_project_skills(errors: list[str]) -> None:
    for skill_file in sorted(PROJECT_SKILLS_ROOT.glob("*/SKILL.md")):
        fields = parse_frontmatter(skill_file)
        relative = skill_file.relative_to(ROOT)
        if not fields:
            errors.append(f"{relative}: missing valid YAML frontmatter")
            continue
        if fields.get("name") != skill_file.parent.name:
            errors.append(
                f"{relative}: name must equal directory "
                f"({skill_file.parent.name})"
            )
        if not fields.get("description"):
            errors.append(f"{relative}: missing description")


def main() -> int:
    errors: list[str] = []
    files = markdown_files()
    check_links(files, errors)
    check_docs_navigation(errors)
    check_old_paths(files, errors)
    check_project_skills(errors)

    if errors:
        print("Documentation validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(
        "Documentation validation passed: "
        f"{len(files)} Markdown files and project Skills checked."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
