# 文档中心

本目录是 LearnPlatform 的正式知识入口。文档与代码一起版本化、评审和验证；同一事实只保留一个权威来源，其他文档通过链接引用。

## 快速入口

- 第一次运行项目：[本地开发](getting-started/local-development.md) 或 [Docker 开发](getting-started/docker-development.md)
- 了解产品：[产品需求](product/prd.md) 与 [产品路线图](product/roadmap.md)
- 了解系统：[系统架构](architecture/overview.md)
- 查询契约：[API 参考](reference/api.md) 与 [数据库参考](reference/database.md)
- 开始开发：[AI Agent 开发工作流](development/workflow.md) 与 [测试策略](development/testing.md)
- 工程协作：[工程规则](development/engineering-rules.md)、[Git 规则](development/git-rules.md)与[审查清单](development/review-checklist.md)
- 查看当前状态：[项目状态](project/status.md)
- 跨对话续接：[Agent 交接](project/handoff.md)
- 查看历史：[开发日志索引](project/changelog/index.md)
- 准备演示：[演示流程](showcase/demo.md)
- 准备项目介绍：[简历材料](showcase/resume.md)

## 信息架构

| 目录 | 内容类型 | 维护原则 |
|---|---|---|
| `getting-started/` | 可执行的启动指南 | 只描述完成任务所需步骤 |
| `product/` | 产品范围、路线图与战略 | 解释做什么、为什么做 |
| `architecture/` | 系统结构和关键决策 | 记录稳定边界与重要取舍 |
| `development/` | 开发、测试、Git、Agent 工作流 | 记录团队执行方式 |
| `reference/` | API、数据库等精确契约 | 与真实代码和迁移保持一致 |
| `project/` | 当前状态、交接和历史日志 | 当前事实与历史记录严格分离 |
| `showcase/` | 演示、简历和截图 | 只能陈述真实已实现能力 |

## 权威来源

| 事实 | 权威文档 |
|---|---|
| 当前阶段、最新验证、遗留问题、下一步 | [project/status.md](project/status.md) |
| 长期阶段规划与候选方向 | [product/roadmap.md](product/roadmap.md) |
| 每轮修改和验证历史 | [project/changelog/](project/changelog/index.md) |
| 产品范围 | [product/prd.md](product/prd.md) |
| 架构边界 | [architecture/overview.md](architecture/overview.md) |
| 重要架构决策 | [architecture/decisions/](architecture/decisions/index.md) |
| API 契约 | [reference/api.md](reference/api.md) |
| 数据库结构 | [reference/database.md](reference/database.md) |
| 测试要求 | [development/testing.md](development/testing.md) |
| Agent 与 Skills 使用边界 | [development/agent-tooling.md](development/agent-tooling.md) |

README 只承担项目入口，不复制动态状态；交接文档只提供续接所需的最小上下文，不复制完整历史。
