#!/usr/bin/env python3
"""Validate LearnPlatform documentation links, navigation, and repository Skills."""

from __future__ import annotations

import re
import sys
import json
from collections import deque
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
DOCS_ROOT = ROOT / "docs"
PROJECT_SKILLS_ROOT = ROOT / ".agents" / "skills"
STATUS_MAX_LINES = 150
STATUS_MAX_CHARACTERS = 10000
NUMBERED_ROUND = re.compile(r"\bRound\s+\d+\b|第\s*[\d一二三四五六七八九十百千万零〇两]+\s*轮", re.IGNORECASE)
PROJECT_SKILL_NAMES = frozenset(
    {"frontend-design", "frontend-flow-test"}
)
UPSTREAM_SKILL_NAMES = frozenset(
    {
        "banner-design",
        "brand",
        "design",
        "design-system",
        "slides",
        "ui-styling",
        "ui-ux-pro-max",
    }
)

MARKDOWN_LINK = re.compile(r"!?\[[^\]]*]\(([^)]+)\)")
FRONTMATTER_FIELD = re.compile(r"^([a-zA-Z0-9_-]+):\s*(.+?)\s*$")
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
FENCED_JSON = re.compile(r"```json\s*\n(.*?)\n```", re.DOTALL)
FENCED_BLOCK = re.compile(r"```.*?\n.*?\n```", re.DOTALL)
METHOD_MAPPING = re.compile(
    r"@(Get|Post|Put|Delete|Patch)Mapping"
    r"\s*\(\s*(?:value\s*=\s*)?\"([^\"]*)\"[^)]*\)"
    r"|@(Get|Post|Put|Delete|Patch)Mapping\s*(?!\()"
)
CLASS_MAPPING = re.compile(r'@RequestMapping\(\s*"([^"]+)"')
CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?",
    re.IGNORECASE,
)

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
    "docs/CHANGELOG_AGENT.md",
    "docs/DEMO.md",
    "docs/RESUME.md",
    "docs/demo-screenshots",
    "skills/skill-creator",
)

OLD_BASENAMES = (
    "PRD.md",
    "ROADMAP.md",
    "FUTURE.md",
    "AI_LEARNING_PLATFORM_STRATEGY.md",
    "ARCHITECTURE.md",
    "API_DESIGN.md",
    "DB_DESIGN.md",
    "AI_AGENT_DEVELOPMENT_WORKFLOW.md",
    "TESTING.md",
    "ENGINEERING_RULES.md",
    "AGENT_GIT_RULES.md",
    "AGENT_REVIEW_CHECKLIST.md",
    "CHANGELOG_AGENT.md",
    "DEMO.md",
    "RESUME.md",
)

CONTENT_RULES: dict[str, tuple[tuple[re.Pattern[str], str], ...]] = {
    "AGENTS.md": (
        (
            re.compile(r"\b20\d{2}-\d{2}-\d{2}\b"),
            "stable rules must not contain a dated project fact",
        ),
        (
            re.compile(r"\bRound\s+\d+\b", re.IGNORECASE),
            "round results belong in status or changelog",
        ),
    ),
    "docs/development/agent-tooling.md": (
        (
            re.compile(r"当前(?:生成)?版本|声明版本"),
            "installed versions belong in changelog, not the maintenance guide",
        ),
        (
            re.compile(r"argument-hint|尾随(?:空格|字符)|git diff --check"),
            "one-off upstream defects belong in changelog",
        ),
    ),
    "docs/development/testing.md": (
        (
            re.compile(r"本轮完成|当前\s*Testcontainers\s*版本"),
            "transient implementation facts do not belong in the test strategy",
        ),
        (
            re.compile(r"现有\s*\d+\s*个.*(?:测试|用例)|真实基线为"),
            "test counts and measured baselines belong in status or changelog",
        ),
    ),
    "docs/product/prd.md": (
        (
            re.compile(r"当前主线|Phase\s*\d+", re.IGNORECASE),
            "current phase belongs in status or roadmap, not the PRD",
        ),
    ),
    "docs/product/roadmap.md": (
        (
            re.compile(r"当前工作：|Round\s*级", re.IGNORECASE),
            "current work and round instructions do not belong in the roadmap",
        ),
    ),
}

BRITTLE_SHOWCASE_FACT = re.compile(
    r"\bV\d+\s*[–—-]\s*V\d+\b|\d+\s*张(?:业务)?表"
)


def markdown_files() -> list[Path]:
    files = [ROOT / "README.md", ROOT / "AGENTS.md"]
    files.extend(sorted(DOCS_ROOT.rglob("*.md")))
    for skill_name in sorted(PROJECT_SKILL_NAMES):
        files.extend(sorted((PROJECT_SKILLS_ROOT / skill_name).rglob("*.md")))
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


def split_link(raw_link: str) -> tuple[str, str]:
    link = raw_link.strip()
    if link.startswith("<") and ">" in link:
        link = link[1 : link.index(">")]
    elif " " in link:
        link = link.split(" ", 1)[0]
    path, separator, fragment = link.partition("#")
    return unquote(path), unquote(fragment) if separator else ""


def heading_anchor(title: str) -> str:
    value = re.sub(r"!?\[([^\]]+)]\([^)]+\)", r"\1", title)
    value = re.sub(r"[`*_~]", "", value).strip().lower()
    value = re.sub(r"\s+", "-", value)
    return re.sub(r"[^\w\u4e00-\u9fff-]", "", value)


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
        text = path.read_text(encoding="utf-8")
        for match in MARKDOWN_LINK.finditer(text):
            link_path, fragment = split_link(match.group(1))
            if link_path.startswith(
                ("http://", "https://", "mailto:", "data:", "app://")
            ):
                continue
            target = (path.parent / link_path).resolve() if link_path else path.resolve()
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
                continue
            if fragment and target.suffix == ".md":
                target_text = FENCED_BLOCK.sub(
                    "", target.read_text(encoding="utf-8")
                )
                anchors = {
                    heading_anchor(heading.group(2))
                    for line in target_text.splitlines()
                    if (heading := HEADING.match(line))
                }
                if fragment.lower() not in anchors:
                    errors.append(
                        f"{path.relative_to(ROOT)}: missing anchor "
                        f"#{fragment} in {target.relative_to(ROOT)}"
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
        for basename in OLD_BASENAMES:
            if re.search(rf"(?<![a-zA-Z0-9_/-]){re.escape(basename)}(?![a-zA-Z0-9_/-])", text):
                errors.append(
                    f"{path.relative_to(ROOT)}: contains legacy filename {basename}"
                )
        for skill_path in (
            "skills/frontend-design",
            "skills/frontend-flow-test",
        ):
            if re.search(
                rf"(?<![a-zA-Z0-9_./-]){re.escape(skill_path)}", text
            ):
                errors.append(
                    f"{path.relative_to(ROOT)}: contains legacy path {skill_path}"
                )


def check_status_snapshot(path: Path, text: str, errors: list[str]) -> None:
    relative = path.relative_to(ROOT).as_posix()
    if len(text.splitlines()) > STATUS_MAX_LINES:
        errors.append(f"{relative}: current snapshot must not exceed {STATUS_MAX_LINES} lines; archive history")
    if len(text) > STATUS_MAX_CHARACTERS:
        errors.append(
            f"{relative}: current snapshot must not exceed {STATUS_MAX_CHARACTERS} characters; archive history"
        )

    def omit_history_link(match: re.Match[str]) -> str:
        link, _ = split_link(match.group(1))
        target = (path.parent / link).resolve()
        if target.suffix == ".md" and any(
            target.is_relative_to((DOCS_ROOT / "project" / directory).resolve())
            for directory in ("changelog", "audits")
        ):
            return ""
        return match.group(0)

    if NUMBERED_ROUND.search(MARKDOWN_LINK.sub(omit_history_link, text)):
        errors.append(f"{relative}: numbered round records belong in history; use a history link for evidence")


def check_document_ownership(files: list[Path], errors: list[str]) -> None:
    history_roots = (
        DOCS_ROOT / "project" / "changelog",
        DOCS_ROOT / "project" / "audits",
    )
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        is_history = any(path.is_relative_to(root) for root in history_roots)

        if not is_history and ".codex/skills" in text:
            errors.append(
                f"{relative}: contains obsolete repository Skill path .codex/skills"
            )

        if path == DOCS_ROOT / "project" / "status.md":
            check_status_snapshot(path, text, errors)
        elif not is_history:
            if re.search(r"\bRound\s+\d+\b", text, re.IGNORECASE):
                errors.append(
                    f"{relative}: numbered round facts belong in changelog or audits"
                )

        for pattern, message in CONTENT_RULES.get(relative, ()):
            match = pattern.search(text)
            if match:
                line = text.count("\n", 0, match.start()) + 1
                errors.append(f"{relative}:{line}: {message}")

        if relative in {
            "README.md",
            "docs/showcase/demo.md",
            "docs/showcase/resume.md",
        }:
            match = BRITTLE_SHOWCASE_FACT.search(text)
            if match:
                line = text.count("\n", 0, match.start()) + 1
                errors.append(
                    f"{relative}:{line}: brittle migration or table count belongs "
                    "in database reference or status"
                )


def check_markdown_structure(files: list[Path], errors: list[str]) -> None:
    for path in files:
        text = path.read_text(encoding="utf-8")
        text = FENCED_BLOCK.sub("", text)
        headings = [
            (len(match.group(1)), match.group(2))
            for line in text.splitlines()
            if (match := HEADING.match(line))
        ]
        h1_count = sum(level == 1 for level, _ in headings)
        is_pull_request_template = path == (
            ROOT / ".github" / "PULL_REQUEST_TEMPLATE.md"
        )
        if not is_pull_request_template and h1_count != 1:
            errors.append(
                f"{path.relative_to(ROOT)}: expected one H1, found {h1_count}"
            )
        previous = 0
        for level, title in headings:
            if previous and level > previous + 1:
                errors.append(
                    f"{path.relative_to(ROOT)}: heading level jumps "
                    f"from H{previous} to H{level} at {title}"
                )
            previous = level


def check_json_examples(files: list[Path], errors: list[str]) -> None:
    for path in files:
        text = path.read_text(encoding="utf-8")
        for index, match in enumerate(FENCED_JSON.finditer(text), start=1):
            try:
                json.loads(match.group(1))
            except json.JSONDecodeError as exc:
                errors.append(
                    f"{path.relative_to(ROOT)}: invalid JSON example "
                    f"#{index} ({exc.msg} at line {exc.lineno})"
                )


def controller_endpoints() -> set[str]:
    endpoints: set[str] = set()
    controller_root = (
        ROOT / "backend" / "src" / "main" / "java" / "com"
        / "learnplatform" / "controller"
    )
    for path in controller_root.glob("*Controller.java"):
        text = path.read_text(encoding="utf-8")
        class_match = CLASS_MAPPING.search(text)
        base = class_match.group(1).rstrip("/") if class_match else ""
        for match in METHOD_MAPPING.finditer(text):
            method = (match.group(1) or match.group(3)).upper()
            suffix = match.group(2) or ""
            full_path = f"{base}/{suffix.lstrip('/')}" if suffix else base
            endpoints.add(f"{method} {full_path}")
    return endpoints


def check_api_inventory(errors: list[str]) -> None:
    api_root = DOCS_ROOT / "reference" / "api"
    if not api_root.exists():
        errors.append("docs/reference/api: API documentation directory is missing")
        return
    documented = "\n".join(
        path.read_text(encoding="utf-8") for path in api_root.glob("*.md")
    )
    for endpoint in sorted(controller_endpoints()):
        if f"`{endpoint}`" not in documented:
            errors.append(f"API endpoint is not documented: {endpoint}")


def migration_tables() -> set[str]:
    migration_root = (
        ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
    )
    tables: set[str] = set()
    for path in migration_root.glob("V*.sql"):
        tables.update(CREATE_TABLE.findall(path.read_text(encoding="utf-8")))
    return tables


def check_database_inventory(errors: list[str]) -> None:
    database_root = DOCS_ROOT / "reference" / "database"
    if not database_root.exists():
        errors.append(
            "docs/reference/database: database documentation directory is missing"
        )
        return
    documented = "\n".join(
        path.read_text(encoding="utf-8") for path in database_root.glob("*.md")
    )
    for table in sorted(migration_tables()):
        if f"`{table}`" not in documented:
            errors.append(f"database table is not documented: {table}")


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


def check_repository_skills(errors: list[str]) -> None:
    names: dict[str, Path] = {}
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
        name = fields.get("name")
        if name:
            if name in names:
                errors.append(
                    f"{relative}: duplicate repository Skill name also used by "
                    f"{names[name].relative_to(ROOT)}"
                )
            names[name] = skill_file
        if skill_file.parent.name not in PROJECT_SKILL_NAMES:
            continue
        metadata = skill_file.parent / "agents" / "openai.yaml"
        if not metadata.exists():
            errors.append(f"{relative}: missing agents/openai.yaml")
            continue
        metadata_text = metadata.read_text(encoding="utf-8")
        for key in ("display_name:", "short_description:", "default_prompt:"):
            if key not in metadata_text:
                errors.append(f"{metadata.relative_to(ROOT)}: missing {key[:-1]}")
        if name and f"${name}" not in metadata_text:
            errors.append(
                f"{metadata.relative_to(ROOT)}: default_prompt must mention ${name}"
            )

    actual_names = set(names)
    expected_names = PROJECT_SKILL_NAMES | UPSTREAM_SKILL_NAMES
    for missing in sorted(expected_names - actual_names):
        errors.append(f".agents/skills/{missing}: expected Skill is missing")
    for unknown in sorted(actual_names - expected_names):
        errors.append(f".agents/skills/{unknown}: ownership is not documented")

    legacy_root = ROOT / ".codex" / "skills"
    if any(legacy_root.glob("*/SKILL.md")):
        errors.append(".codex/skills: legacy repository Skills must be removed")


def main() -> int:
    errors: list[str] = []
    files = markdown_files()
    check_links(files, errors)
    check_docs_navigation(errors)
    check_old_paths(files, errors)
    check_document_ownership(files, errors)
    check_markdown_structure(files, errors)
    check_json_examples(files, errors)
    check_repository_skills(errors)
    check_api_inventory(errors)
    check_database_inventory(errors)

    if errors:
        print("Documentation validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(
        "Documentation validation passed: "
        f"{len(files)} Markdown files and repository Skills checked."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
