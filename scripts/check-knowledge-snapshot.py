#!/usr/bin/env python3
"""Validate the versioned 408 knowledge snapshot without the AiStu checkout."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any


DEFAULT_ROOT = Path(__file__).resolve().parents[1] / "content/knowledge/cs408/v1"


def read_json(path: Path, errors: list[str]) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise ValueError("expected a JSON object")
        return value
    except (OSError, ValueError) as error:
        errors.append(f"{path}: {error}")
        return {}


def read_jsonl(paths: list[Path], errors: list[str]) -> list[dict[str, Any]]:
    if not paths:
        errors.append("missing JSONL files")
        return []
    rows: list[dict[str, Any]] = []
    for path in paths:
        try:
            with path.open(encoding="utf-8") as source:
                for number, line in enumerate(source, 1):
                    value = json.loads(line)
                    if not isinstance(value, dict):
                        raise ValueError(f"line {number} is not a JSON object")
                    rows.append(value)
        except (OSError, ValueError) as error:
            errors.append(f"{path}: {error}")
    return rows


def record_map(rows: list[dict[str, Any]], key: str, label: str,
               errors: list[str]) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        identifier = row.get(key)
        if not isinstance(identifier, str) or not identifier or identifier in result:
            errors.append(f"{label}: missing or duplicate {key}: {identifier!r}")
        else:
            result[identifier] = row
    return result


def validate_files(root: Path, manifest: dict[str, Any], errors: list[str]) -> None:
    files = manifest.get("files")
    if not isinstance(files, dict) or not files:
        errors.append("manifest files must contain sha256 entries")
        return
    expected: set[str] = set()
    for relative, digest in files.items():
        if not isinstance(relative, str) or not isinstance(digest, str):
            errors.append("manifest file paths and sha256 values must be strings")
            continue
        path = PurePosixPath(relative)
        if (path.is_absolute() or ".." in path.parts or path.as_posix() != relative
                or relative == "manifest.json"):
            errors.append(f"invalid manifest path: {relative}")
            continue
        expected.add(relative)
        target = root / relative
        if not target.is_file() or target.is_symlink():
            errors.append(f"missing or linked snapshot file: {relative}")
            continue
        actual = hashlib.sha256(target.read_bytes()).hexdigest()
        if not re.fullmatch(r"[0-9a-f]{64}", digest) or actual != digest:
            errors.append(f"sha256 mismatch: {relative}")
    actual_files = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file() and path.relative_to(root).as_posix() != "manifest.json"
    }
    for relative in sorted(actual_files - expected):
        errors.append(f"unlisted snapshot file: {relative}")


def validate_snapshot(root: Path) -> list[str]:
    errors: list[str] = []
    manifest = read_json(root / "manifest.json", errors)
    if not manifest:
        return errors
    course_id = manifest.get("course_id")
    if manifest.get("schema_version") != 1 or course_id != "cs408-data-structures":
        errors.append("manifest schema_version or course_id is unsupported")
    if not re.fullmatch(r"[0-9a-f]{40}", str(manifest.get("source_revision", ""))):
        errors.append("manifest source_revision must be a Git commit")
    if manifest.get("quality_status") not in {"review_pending", "reviewed", "revision_required"}:
        errors.append("manifest quality_status is invalid")
    validate_files(root, manifest, errors)

    taxonomy = read_json(root / "taxonomy.json", errors)
    coverage = read_json(root / "coverage.json", errors)
    if taxonomy.get("course_id") != course_id:
        errors.append("taxonomy course_id differs from manifest")
    if coverage.get("course_id") != course_id:
        errors.append("coverage course_id differs from manifest")
    if coverage.get("review_status") != manifest.get("quality_status"):
        errors.append("coverage review_status differs from manifest")

    published = record_map(
        read_jsonl(sorted((root / "concepts/published").glob("*.jsonl")), errors),
        "id", "published concepts", errors,
    )
    internal = record_map(
        read_jsonl(sorted((root / "concepts/internal").glob("*.jsonl")), errors),
        "id", "internal concepts", errors,
    )
    sources = record_map(
        read_jsonl(sorted((root / "sources").glob("*.jsonl")), errors),
        "id", "source records", errors,
    )
    chunks = record_map(
        read_jsonl([root / "rag/chunks.jsonl"], errors),
        "chunk_id", "RAG chunks", errors,
    )
    relations = read_jsonl([root / "relations/relations.jsonl"], errors)
    source_manifests = sorted((root / "source_manifests").glob("*.json"))
    if not source_manifests:
        errors.append("source manifests are missing")
    if ({path.stem for path in source_manifests}
            != {path.stem for path in (root / "concepts/published").glob("*.jsonl")}):
        errors.append("source manifests do not match published chapters")
    for path in source_manifests:
        source_manifest = read_json(path, errors)
        if source_manifest.get("review_status") != manifest.get("quality_status"):
            errors.append(f"source manifest review_status differs: {path.name}")

    if set(published) != set(internal):
        errors.append("internal and published concept IDs differ")
    for identifier, concept in published.items():
        if concept.get("location", {}).get("course_id") != course_id:
            errors.append(f"concept course_id differs: {identifier}")
        if concept.get("quality", {}).get("status") != manifest.get("quality_status"):
            errors.append(f"concept quality status differs: {identifier}")
        internal_concept = internal.get(identifier, {})
        if ({key: value for key, value in internal_concept.items()
             if key != "source_record_ids"} != concept):
            errors.append(f"internal and published concepts differ: {identifier}")
    source_refs = {
        source_id
        for concept in internal.values()
        for source_id in concept.get("source_record_ids", [])
    }
    if source_refs - set(sources):
        errors.append("internal concepts reference missing source records")

    for identifier, chunk in chunks.items():
        if chunk.get("metadata", {}).get("course_id") != course_id:
            errors.append(f"RAG chunk course_id differs: {identifier}")
        if chunk.get("concept_id") not in published:
            errors.append(f"RAG chunk references missing concept: {identifier}")
    indexed_concepts = {chunk.get("concept_id") for chunk in chunks.values()}
    if set(published) - indexed_concepts:
        errors.append("published concepts without RAG chunks")
    for relation in relations:
        if relation.get("source_id") not in published or relation.get("target_id") not in published:
            errors.append("relation references missing concept")

    items = coverage.get("items", [])
    chapters = set(coverage.get("chapter_ids", []))
    if not isinstance(items, list) or not isinstance(coverage.get("chapter_ids"), list):
        errors.append("coverage items or chapter_ids is invalid")
        items, chapters = [], set()
    mapped = {
        concept_id
        for item in items if isinstance(item, dict)
        for concept_id in item.get("concept_ids", [])
    }
    content_ids = {
        identifier for identifier, concept in published.items()
        if concept.get("location", {}).get("chapter_id") in chapters
    }
    if (mapped != content_ids or any(not isinstance(item, dict) or not item.get("concept_ids")
                                     for item in items)
            or coverage.get("uncovered_item_ids") != []):
        errors.append("coverage mapping differs from published content concepts")
    if (coverage.get("official_leaf_count") != len(items)
            or coverage.get("content_concept_count") != len(content_ids)):
        errors.append("coverage declared counts differ from content")

    counts = manifest.get("counts", {})
    actual_counts = {
        "concepts": len(published), "chunks": len(chunks),
        "coverage_items": len(items), "relations": len(relations),
    }
    if counts != actual_counts:
        errors.append(f"manifest counts differ: expected {counts}, actual {actual_counts}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    arguments = parser.parse_args()
    errors = validate_snapshot(arguments.root)
    if errors:
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Knowledge snapshot validation passed: {arguments.root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
