# 数据库参考

LearnPlatform 使用 MySQL 8，结构由 Flyway 迁移管理。运行时数据库结构以 [迁移目录](../../../backend/src/main/resources/db/migration/) 中按版本执行的 SQL 为唯一事实来源；本目录解释领域归属、关键约束和演进规则。

## 领域导航

| 文档 | 内容 |
|---|---|
| [模型总览](model-overview.md) | 领域边界、表清单、关系与通用字段 |
| [学习内容域](learning-domain.md) | 用户、课程、知识点、题目、投稿和互动 |
| [练习与考试域](assessment-domain.md) | 练习、错题、复习、试卷和考试 |
| [AI 与治理域](ai-and-governance.md) | AI 调用、资产、变式训练、配额和提醒 |
| [迁移策略](migration-policy.md) | Flyway 规则、约束和验证方式 |

## 维护规则

迁移编写、存量兼容和外键取舍统一见[迁移策略](migration-policy.md)。表、索引或存储语义变化时，
更新对应领域文档；迁移目录提供实际版本与 SQL，不另行维护最新版本或逐版本变化表。
文档只说明约束与语义，不复制完整 DDL。`scripts/check-docs.py` 核对迁移创建的表是否已有文档入口。
