# 408 数据结构知识快照

`v1/` 是从 AiStu 桌面端仓库导入 LearnPlatform 的版本化 408 内容快照。它只包含
`cs408-data-structures` 课程，不包含 AiStu 的 ODS 试点、虚拟环境、编写脚本或用户学习状态。
上游仓库提交、文件 SHA-256、课程 ID、质量状态和数量记录在
[清单](v1/manifest.json)中。

| 路径 | 用途 |
|---|---|
| `v1/concepts/published/` | 面向检索和展示的知识点正文及稳定 ID |
| `v1/rag/chunks.jsonl` | 从知识点派生的检索片段；当前仅含 408 课程 |
| `v1/taxonomy.json`、`v1/coverage.json` | 课程结构与 2026 考纲映射 |
| `v1/relations/relations.jsonl` | 知识点关系 |
| `v1/concepts/internal/`、`v1/sources/`、`v1/source_manifests/` | 内部溯源和审查资料，不直接作为用户展示内容 |

当前 139 个知识点和 556 个检索片段的质量状态均为 `review_pending`；文件中的
`published` 表示公开投影格式，不表示已通过学科审查。来源网址与原始报告指纹是
来源记录，不能替代逐条内容审查；适用许可也尚未在本快照中完成核对。

运行 `python3 scripts/check-knowledge-snapshot.py` 核对哈希、课程隔离、知识点引用、
考纲映射和数量。快照可通过[管理端导入接口](../../../docs/reference/api/admin-governance.md#知识快照导入)存入 MySQL；
云 Embedding 与 Qdrant/Tutor RAG 接入默认关闭；本快照未经人工审核，不会自动进入检索。更新内容时应创建新版本目录并保留旧版本的标识与清单，
避免直接改写已被调用引用的知识片段。
