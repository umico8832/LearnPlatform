# 数据库参考

LearnPlatform 使用 MySQL 8，结构由 Flyway 迁移管理。运行时数据库结构以 `backend/src/main/resources/db/migration/` 中按版本执行的 SQL 为唯一事实来源；本目录解释领域归属、关键约束和演进规则。

## 领域导航

| 文档 | 内容 |
|---|---|
| [模型总览](model-overview.md) | 领域边界、表清单、关系与通用字段 |
| [学习内容域](learning-domain.md) | 用户、课程、知识点、题目、投稿和互动 |
| [练习与考试域](assessment-domain.md) | 练习、错题、复习、试卷和考试 |
| [AI 与治理域](ai-and-governance.md) | AI 调用、资产、变式训练、配额和提醒 |
| [迁移策略](migration-policy.md) | Flyway 规则、约束和验证方式 |

## 当前基线

- 数据库：MySQL 8。
- 迁移：V1–V63。
- 基线迁移：`V1__baseline.sql`。
- 最新迁移：`V63__seed_reviewed_sequential_vs_linked_tutor_content.sql`。
- 逻辑外键为主，跨聚合一致性由 Service 事务和数据库唯一约束共同保证。

## 维护规则

1. 禁止通过修改历史迁移改变已发布结构。
2. 表或索引变化必须新增 Flyway 迁移，并同步对应领域文档。
3. 文档不复制完整 DDL，避免与迁移形成两份结构事实。
4. `scripts/check-docs.py` 会核对迁移中创建的表是否进入数据库文档。
