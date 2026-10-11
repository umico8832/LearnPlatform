"""Regression checks for the M5 draft structure validator."""

from __future__ import annotations

import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "validate-question-drafts.py"
SPEC = importlib.util.spec_from_file_location("validate_question_drafts", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def draft() -> dict:
    options = [{"label": label, "content": label + " 的描述"} for label in "ABCD"]
    pending = {"kind": "AI_GENERATED", "reviewStatus": "PENDING", "label": "AI 生成·待审核"}
    return {
        "schemaVersion": 1,
        "section": "栈",
        "conventions": "docs/product/explanation-conventions.md",
        "questions": [{
            "id": "test-01", "topic": "栈", "source": {"kind": "ORIGINAL", "label": "原创", "url": None},
            "contentSource": pending, "stem": "哪个选项正确？", "options": options,
            "correctAnswer": "B",
            "misconceptions": {label: {"title": "误区" + label, "explanation": "解释" + label}
                               for label in "ACD"},
            "hints": [{"level": i, "text": "提示" + str(i)} for i in (1, 2, 3)],
            "solution": "完整思路", "viz": None,
            "variant": {"stem": "另一条件下哪个正确？", "options": options,
                        "correctAnswer": "C", "changedCondition": "条件变化", "explanation": "变式解析",
                        "viz": None, "source": pending},
        }],
    }


class DraftValidationTest(unittest.TestCase):
    def validate(self, document: dict) -> tuple[int, int]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.json"
            path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
            return MODULE.validate_files((path,), require_target_count=False)

    def test_accepts_complete_pending_draft(self) -> None:
        self.assertEqual((1, 0), self.validate(draft()))

    def test_rejects_answer_not_in_options(self) -> None:
        document = draft()
        document["questions"][0]["correctAnswer"] = "E"
        with self.assertRaisesRegex(ValueError, "答案必须指向现有选项"):
            self.validate(document)

    def test_requires_each_wrong_option_and_unreviewed_provenance(self) -> None:
        document = draft()
        del document["questions"][0]["misconceptions"]["D"]
        with self.assertRaisesRegex(ValueError, "每个错项"):
            self.validate(document)
        document = copy.deepcopy(draft())
        document["questions"][0]["contentSource"]["reviewStatus"] = "REVIEWED"
        with self.assertRaisesRegex(ValueError, "待审核"):
            self.validate(document)


if __name__ == "__main__":
    unittest.main()
