# 题目作者源

本层 JSON 对应已经用迁移入库的作者源；`unimported/` 只保存未入库的草稿。内容审核状态如实写在各题中，目录位置不授予审核。

`m5-*.json` 是既有批次；`p27-*.json`（含子目录）每批8–10题，统一由 `scripts/validate-question-drafts.py` 校验结构、批量标识唯一性与正式场景。迁移回归只读取本层已入库作者源。未入库题目的动画候选写在 `vizCandidates`，实际 `viz` 保持空，不将候选当作已实现的场景。

讲解、代码标记、来源和审核方式以 `docs/product/explanation-conventions.md` 为准。
