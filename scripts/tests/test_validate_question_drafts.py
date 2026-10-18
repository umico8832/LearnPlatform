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
    def validate_unimported(self, document: dict) -> tuple[int, int]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "unimported" / "sample.json"
            path.parent.mkdir()
            path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
            return MODULE.validate_files((path,), require_target_count=False)

    def test_unimported_batches_require_pending_candidates_without_live_visualizations(self) -> None:
        document = draft()
        with self.assertRaisesRegex(ValueError, "候选"):
            self.validate_unimported(document)
        candidate = {"id": "example", "status": "CANDIDATE", "algorithm": "dfs",
                     "input": {"nodes": ["A"], "edges": []},
                     "goal": "观察访问集合", "pauseQuestion": "下一步访问谁？"}
        question = document["questions"][0]
        question["vizCandidates"] = [candidate]
        question["variant"]["vizCandidates"] = [dict(candidate, id="example-variant")]
        self.assertEqual((1, 0), self.validate_unimported(document))
        for target in ("content", "variant"):
            with self.subTest(target=target):
                reviewed_document = copy.deepcopy(document)
                reviewed_question = reviewed_document["questions"][0]
                reviewed = {"kind": "AI_GENERATED", "reviewStatus": "REVIEWED", "label": "AI 生成·已审核",
                            "reviewNote": "独立 AI 审核：核对记录 test-evidence"}
                if target == "content":
                    reviewed_question["contentSource"] = reviewed
                else:
                    reviewed_question["variant"]["source"] = reviewed
                with self.assertRaisesRegex(ValueError, "保持待审核"):
                    self.validate_unimported(reviewed_document)
        question["viz"] = {"title": "访问过程", "spec": {}}
        with self.assertRaisesRegex(ValueError, "不挂动画"):
            self.validate_unimported(document)

    def validate(self, document: dict) -> tuple[int, int]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.json"
            path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
            return MODULE.validate_files((path,), require_target_count=False)

    def test_accepts_complete_pending_draft(self) -> None:
        self.assertEqual((1, 0), self.validate(draft()))

    def test_rejects_code_markup_in_plain_visualization_titles(self) -> None:
        for part in ("solution", "variant", "misconception"):
            with self.subTest(part=part):
                document = draft()
                question = document["questions"][0]
                target = (question if part == "solution" else question["variant"]
                          if part == "variant" else question["misconceptions"]["A"])
                target["viz"] = {"title": "先序访问 `A`", "spec": {}}
                with self.assertRaisesRegex(ValueError, "纯文本"):
                    self.validate(document)

    def test_rejects_unquoted_code_in_all_reader_text(self) -> None:
        for code in ("p->next=s", "data[i-1]", "front==rear", "Push(1)",
                     "free(q)", "(rear+1)%M==front", "8->next=L", "data[]", "top=-1", "++top", "next", "prior"):
            with self.subTest(code=code):
                document = draft()
                document["questions"][0]["hints"][2]["text"] = "例如 " + code + "。"
                with self.assertRaisesRegex(ValueError, "反引号"):
                    self.validate(document)
        document = draft()
        document["questions"][0]["variant"]["options"][0]["content"] = "令 p->next=s"
        with self.assertRaisesRegex(ValueError, "反引号"):
            self.validate(document)

    def test_rejects_partially_quoted_function_forms(self) -> None:
        for code in ("`O(n`²)", "`Push(`甲)", "Pop"):
            with self.subTest(code=code):
                document = draft()
                document["questions"][0]["hints"][2]["text"] = code
                with self.assertRaisesRegex(ValueError, "反引号"):
                    self.validate(document)

    def test_accepts_inline_code_and_plain_chinese_parentheses(self) -> None:
        document = draft()
        document["questions"][0]["stem"] = (
            "执行 `p->next=s`、`data[i-1]`、`front==rear`、`Push(1)`（示意）。"
            "再看 ``GetTop(S)``、`next`、`prior` 与第 1 个（头结点不计）。"
        )
        self.assertEqual((1, 0), self.validate(document))

    def test_accepts_reviewed_ai_with_recorded_review_method(self) -> None:
        for note in ("AI 初稿 + Codex 冷读 + Claude 页面审核",
                     "独立 AI 审核：新会话 review-test；模型 test-model；核对记录 test-evidence"):
            with self.subTest(note=note):
                document = draft()
                reviewed = {"kind": "AI_GENERATED", "reviewStatus": "REVIEWED", "label": "AI 生成·已审核",
                            "reviewNote": note}
                document["questions"][0]["contentSource"] = reviewed
                document["questions"][0]["variant"]["source"] = reviewed
                self.assertEqual((1, 0), self.validate(document))

    def test_reviewed_content_and_variant_require_nonempty_review_notes(self) -> None:
        for target in ("content", "variant"):
            for note in (None, "", "  ", 7, ["独立 AI 审核"]):
                with self.subTest(target=target, note=note):
                    document = draft()
                    reviewed = {"kind": "AI_GENERATED", "reviewStatus": "REVIEWED", "label": "AI 生成·已审核"}
                    if note is not None:
                        reviewed["reviewNote"] = note
                    question = document["questions"][0]
                    if target == "content":
                        question["contentSource"] = reviewed
                    else:
                        question["variant"]["source"] = reviewed
                    with self.assertRaisesRegex(ValueError, "审核"):
                        self.validate(document)

    def test_review_note_cannot_replace_valid_review_state(self) -> None:
        for field, value in (("kind", "CURATED"), ("reviewStatus", "APPROVED"),
                             ("label", "AI 生成·待审核")):
            with self.subTest(field=field):
                document = draft()
                source = {"kind": "AI_GENERATED", "reviewStatus": "REVIEWED", "label": "AI 生成·已审核",
                          "reviewNote": "独立 AI 审核：核对记录 test-evidence"}
                source[field] = value
                document["questions"][0]["contentSource"] = source
                with self.assertRaisesRegex(ValueError, "审核"):
                    self.validate(document)

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
