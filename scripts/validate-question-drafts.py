#!/usr/bin/env python3
"""Validate authored question batches and run the real frontend scene simulators."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAFTS = tuple(sorted((ROOT / "content/drafts").glob("m5-*.json")))
P27_DRAFTS = tuple(sorted((ROOT / "content/drafts").rglob("p27-*.json")))
LABELS = {"A", "B", "C", "D"}
PENDING = {"kind": "AI_GENERATED", "reviewStatus": "PENDING", "label": "AI 生成·待审核"}
REVIEWED = {"kind": "AI_GENERATED", "reviewStatus": "REVIEWED", "label": "AI 生成·已审核"}


def require(condition: bool, where: str, message: str) -> None:
    if not condition:
        raise ValueError(f"{where}: {message}")


def options(where: str, rows: object, answer: object) -> None:
    require(isinstance(rows, list) and len(rows) == 4, where, "需要四个选项")
    labels = [row.get("label") for row in rows if isinstance(row, dict)]
    require(len(labels) == 4 and set(labels) == LABELS, where, "选项标签必须恰为 A–D")
    require(all(isinstance(row.get("content"), str) and row["content"].strip() for row in rows),
            where, "选项内容不能为空")
    require(len({row["content"] for row in rows}) == 4, where, "选项内容不能重复")
    require(answer in LABELS, where, "答案必须指向现有选项")
    for row in rows:
        text(where + ".options." + row["label"], row["content"])


def text(where: str, value: object) -> None:
    require(isinstance(value, str) and bool(value.strip()), where, "文字不能为空")
    for span in re.finditer(r"(`+)(?:(?!\1).)*?\1", value, flags=re.DOTALL):
        code = span.group().strip("`")
        if re.search(r"[A-Za-z_]\w*\s*\(", code):
            require(code.count("(") == code.count(")"), where, "函数形式须完整放在同一对反引号内")
    prose = re.sub(r"(`+)(?:(?!\1).)*?\1", "", value, flags=re.DOTALL)
    require(not re.search(r"->|\[[^]\n]*\]|[=!<>]=|[A-Za-z_]\w*\s*=|\+\+|--|[A-Za-z_]\w*\s*\(|\b(?:Push|Pop|GetTop|EnQueue|DeQueue|next|prior)\b", prose),
            where, "代码表达式必须放在反引号内")


def viz(where: str, value: object) -> int:
    if value is None:
        return 0
    require(isinstance(value, dict), where, "可视化必须是对象或 null")
    text(where + ".title", value.get("title"))
    require("`" not in value["title"], where, "动画标题按纯文本呈现，代码标记请放在正文")
    require(isinstance(value.get("spec"), dict), where, "可视化 spec 必须是对象")
    return 1


def content_source(where: str, value: object) -> None:
    require(isinstance(value, dict), where, "缺少内容审核状态")
    if value == PENDING:
        return
    metadata = {key: item for key, item in value.items() if key != "reviewNote"}
    require(metadata == REVIEWED, where, "内容须为 AI 生成的待审核或已审核状态")
    note = value.get("reviewNote")
    require(isinstance(note, str) and bool(note.strip()), where, "已审核内容须记录实际审核方式与核对依据")


def validate_question(question: dict, where: str) -> int:
    text(where + ".id", question.get("id"))
    text(where + ".topic", question.get("topic"))
    text(where + ".stem", question.get("stem"))
    source = question.get("source")
    require(isinstance(source, dict), where, "缺少题目来源")
    require(source.get("kind") in {"ORIGINAL", "OFFICIAL_EXAM"}, where, "题目来源类型无效")
    if source["kind"] == "ORIGINAL":
        require(source.get("label") == "原创" and source.get("url") is None,
                where, "原创题应标注原创且不伪造 URL")
    else:
        text(where + ".source.year", source.get("year"))
        text(where + ".source.questionNumber", source.get("questionNumber"))
        require(isinstance(source.get("url"), str) and source["url"].startswith("https://"),
                where, "408 真题需要可访问链接")
    content_source(where + ".contentSource", question.get("contentSource"))

    answer = question.get("correctAnswer")
    options(where, question.get("options"), answer)
    wrong = question.get("misconceptions")
    require(isinstance(wrong, dict) and set(wrong) == LABELS - {answer},
            where, "每个错项须有且只有一条误区")
    count = viz(where + ".viz", question.get("viz"))
    for label, explanation in wrong.items():
        item_where = f"{where}.misconceptions.{label}"
        require(isinstance(explanation, dict), item_where, "误区必须是对象")
        text(item_where + ".title", explanation.get("title"))
        text(item_where + ".explanation", explanation.get("explanation"))
        count += viz(item_where + ".viz", explanation.get("viz"))

    hints = question.get("hints")
    require(isinstance(hints, list) and all(isinstance(hint, dict) for hint in hints)
            and [hint.get("level") for hint in hints] == [1, 2, 3],
            where, "提示必须依次为 1、2、3 级")
    for hint in hints:
        text(f"{where}.hint{hint['level']}", hint.get("text"))
    text(where + ".solution", question.get("solution"))

    variant = question.get("variant")
    require(isinstance(variant, dict), where, "缺少无提示变式")
    text(where + ".variant.stem", variant.get("stem"))
    text(where + ".variant.changedCondition", variant.get("changedCondition"))
    text(where + ".variant.explanation", variant.get("explanation"))
    options(where + ".variant", variant.get("options"), variant.get("correctAnswer"))
    content_source(where + ".variant.source", variant.get("source"))
    count += viz(where + ".variant.viz", variant.get("viz"))
    return count


def candidate_ids(where: str, values: object) -> list[str]:
    require(isinstance(values, list) and bool(values), where, "未入库题目需要动画候选设计意图")
    ids = []
    for index, value in enumerate(values):
        place = f"{where}[{index}]"
        require(isinstance(value, dict), place, "候选必须为对象")
        for field in ("id", "algorithm", "goal", "pauseQuestion"):
            text(place + "." + field, value.get(field))
        require(value.get("status") == "CANDIDATE", place, "候选不能标为已实现场景")
        require(isinstance(value.get("input"), dict) and bool(value["input"]), place, "候选需要输入数据")
        ids.append(value["id"])
    return ids


def validate_files(paths: tuple[Path, ...], require_target_count: bool = True) -> tuple[int, int]:
    require(bool(paths), "草稿", "找不到内容文件")
    seen: set[str] = set()
    seen_candidates: set[str] = set()
    count = 0
    visualizations = 0
    for path in paths:
        document = json.loads(path.read_text(encoding="utf-8"))
        require(document.get("schemaVersion") == 1, str(path), "Schema 版本不匹配")
        text(str(path) + ".section", document.get("section"))
        require(document.get("conventions") == "docs/product/explanation-conventions.md",
                str(path), "未引用全站讲解口径")
        questions = document.get("questions")
        require(isinstance(questions, list) and questions, str(path), "文件内没有题目")
        for question in questions:
            where = f"{path.name}:{question.get('id', '?') if isinstance(question, dict) else '?'}"
            require(isinstance(question, dict), where, "题目必须是对象")
            question_visualizations = validate_question(question, where)
            if "unimported" in path.parts:
                require(question_visualizations == 0, where, "未入库草稿不挂动画，实际 viz 必须为空")
                require(question["contentSource"] == PENDING and question["variant"]["source"] == PENDING,
                        where, "未入库草稿保持待审核")
                ids = (candidate_ids(where + ".vizCandidates", question.get("vizCandidates"))
                       + candidate_ids(where + ".variant.vizCandidates", question["variant"].get("vizCandidates")))
                for candidate_id in ids:
                    require(candidate_id not in seen_candidates, where, "动画候选 ID 重复")
                    seen_candidates.add(candidate_id)
            visualizations += question_visualizations
            require(question["id"] not in seen, where, "题目 ID 重复")
            seen.add(question["id"])
            count += 1
    if require_target_count:
        require(20 <= count <= 30, "草稿", "本批目标为 20–30 道完整题")
    return count, visualizations


def main() -> int:
    try:
        validate_files((*DRAFTS, *P27_DRAFTS), require_target_count=False)
        count, visualizations = validate_files(DRAFTS)
        for path in P27_DRAFTS:
            batch_count, batch_visualizations = validate_files((path,), require_target_count=False)
            require(8 <= batch_count <= 10, path.name, "每批目标为 8–10 道完整题")
            count += batch_count
            visualizations += batch_visualizations
    except (ValueError, json.JSONDecodeError, KeyError, TypeError) as error:
        print(f"草稿结构校验失败：{error}", file=sys.stderr)
        return 1
    print(f"草稿结构通过：{count} 道题，{visualizations} 个场景描述")
    result = subprocess.run(
        ["npm", "test", "--", "src/__tests__/content-drafts.test.ts"],
        cwd=ROOT / "frontend", check=False,
    )
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
