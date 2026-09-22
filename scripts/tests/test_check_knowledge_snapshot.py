from __future__ import annotations

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-knowledge-snapshot.py"
SPEC = importlib.util.spec_from_file_location("check_knowledge_snapshot", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载知识快照检查脚本")
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False) + "\n", encoding="utf-8")


class KnowledgeSnapshotValidationTest(unittest.TestCase):
    def create_snapshot(self, root: Path) -> None:
        course_id = "cs408-data-structures"
        concept = {
            "id": "cs408-example",
            "location": {"course_id": course_id, "chapter_id": "408-example"},
            "quality": {"status": "review_pending"},
        }
        write_json(root / "concepts/published/408-example.jsonl", concept)
        write_json(
            root / "concepts/internal/408-example.jsonl",
            {**concept, "source_record_ids": ["source-example"]},
        )
        write_json(root / "sources/408-example.jsonl", {"id": "source-example"})
        write_json(root / "source_manifests/408-example.json", {"review_status": "review_pending"})
        write_json(root / "taxonomy.json", {"course_id": course_id})
        write_json(
            root / "coverage.json",
            {
                "course_id": course_id,
                "official_leaf_count": 1,
                "content_concept_count": 1,
                "chapter_ids": ["408-example"],
                "items": [{"item_id": "leaf", "concept_ids": ["cs408-example"]}],
                "uncovered_item_ids": [],
                "review_status": "review_pending",
            },
        )
        write_json(
            root / "rag/chunks.jsonl",
            {
                "chunk_id": "rag-example",
                "concept_id": "cs408-example",
                "metadata": {"course_id": course_id},
            },
        )
        write_json(
            root / "relations/relations.jsonl",
            {"source_id": "cs408-example", "target_id": "cs408-example"},
        )
        files = {
            path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in root.rglob("*")
            if path.is_file()
        }
        write_json(
            root / "manifest.json",
            {
                "schema_version": 1,
                "course_id": course_id,
                "source_revision": "a" * 40,
                "quality_status": "review_pending",
                "counts": {"concepts": 1, "chunks": 1, "coverage_items": 1, "relations": 1},
                "files": files,
            },
        )

    def test_accepts_complete_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_snapshot(root)
            self.assertEqual([], checker.validate_snapshot(root))

    def test_rejects_modified_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_snapshot(root)
            (root / "rag/chunks.jsonl").write_text("{}\n", encoding="utf-8")
            errors = checker.validate_snapshot(root)
            self.assertTrue(any("sha256" in error for error in errors), errors)

    def test_rejects_other_course_even_with_matching_checksum(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_snapshot(root)
            chunk = root / "rag/chunks.jsonl"
            data = json.loads(chunk.read_text(encoding="utf-8"))
            data["metadata"]["course_id"] = "open-data-structures"
            write_json(chunk, data)
            manifest_path = root / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"]["rag/chunks.jsonl"] = hashlib.sha256(chunk.read_bytes()).hexdigest()
            write_json(manifest_path, manifest)
            errors = checker.validate_snapshot(root)
            self.assertTrue(any("course_id" in error for error in errors), errors)

    def test_rejects_unmapped_content(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_snapshot(root)
            coverage_path = root / "coverage.json"
            coverage = json.loads(coverage_path.read_text(encoding="utf-8"))
            coverage["items"][0]["concept_ids"] = ["cs408-missing"]
            write_json(coverage_path, coverage)
            manifest_path = root / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"]["coverage.json"] = hashlib.sha256(coverage_path.read_bytes()).hexdigest()
            write_json(manifest_path, manifest)
            errors = checker.validate_snapshot(root)
            self.assertTrue(any("coverage" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
