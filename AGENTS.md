# LearnPlatform Agent 入口

LearnPlatform 是用于个人学习与简历展示的 Web 优先 AI 课程学习平台，以课程长期学习过程连接
AI 教学、试卷学习、练习、错题、复习和测评。产品范围与优先级见[产品需求](docs/product/prd.md)。

本文件只保留每次任务都适用的底线和读取路由；详细规则由对应文档唯一维护。

## 必要底线

- 回答和决策保持客观，区分事实、推测与建议；不编造实现、来源、验证或交付结果。
- 用户当前要求优先，只处理授权范围；只读问答、审查和调研不修改文件。
  小范围实现选择可按证据自行决定；重大方向、破坏性操作或需求冲突缺少授权时暂停确认。
- 修改前检查工作区，保护用户已有改动；不夹带无关修改，不引入无必要架构。
- 不以伪代码、假数据、硬编码或临时代码冒充完成，不为测试通过弱化权限、判分、安全与数据一致性。
- 禁止读取、输出或提交 `.env`、真实密钥、Token、Cookie、数据库密码和隐私数据；`.env.example` 仅含示例值。
- 未经明确授权，不执行 push、rebase、reset、force push、清理历史、删除分支或 tag，
  不删除用户数据或清理其他项目资源。Docker 清理必须先核对专门文档中的授权边界。
- 修改后按实际风险验证，报告未执行项；提交前核对验收边界，不把一次对话结束当作自动提交条件。

## 开始任务

1. 新任务或交接时读取本文件和[当前状态](docs/project/status.md)，确认当前目标、验证缺口与授权范围。
2. 按下表在对应动作之前读取相关文档；任务涉及多个领域时合并必要读取项，已读且未变化的内容不重复读取。
3. 仅在需要了解项目、规划或追溯历史时读取 README、roadmap 和相关月份日志，不默认全文加载历史。

专项规则不能弱化上述底线；同一事实的权威来源与文档冲突处理见[文档中心](docs/index.md#权威来源)。

## 按任务读取

| 触发条件与读取时机 | 必读入口 |
|---|---|
| 修改代码、配置、检查工具或协作规则前 | [开发工作流](docs/development/workflow.md)；按改动风险读取[测试策略](docs/development/testing.md) |
| 决定新增测试或选择验证范围前 | [测试策略](docs/development/testing.md) |
| 持续开发、恢复维护任务或判断任务完成前 | [执行模式与阶段边界](docs/development/workflow.md#6-执行模式与阶段边界)、[完成状态](docs/development/workflow.md#5-完成状态) |
| 判断功能范围、规划下一阶段前 | [产品需求](docs/product/prd.md)、[路线图](docs/product/roadmap.md)；候选方向从[文档中心](docs/index.md)进入 |
| 调整模块、目录或职责边界前 | [工程规则](docs/development/engineering-rules.md)、[系统架构](docs/architecture/overview.md)；重大决策查阅[ADR](docs/architecture/decisions/index.md) |
| 修改接口、鉴权或请求响应契约前 | [API 参考](docs/reference/api/index.md)及对应业务域文档；行为验证遵循测试策略 |
| 修改数据库结构、约束或迁移前 | [数据库参考](docs/reference/database/index.md)、[迁移策略](docs/reference/database/migration-policy.md)和测试策略 |
| 修改前端视觉、布局或交互前 | [前端任务路由](docs/development/agent-tooling.md#前端任务)，依次读取其指定的设计 Skills |
| 打开浏览器做临时流程验收前 | [frontend-flow-test](.agents/skills/frontend-flow-test/SKILL.md)、[测试策略](docs/development/testing.md#9-agent-临时浏览器流程验收) |
| 修改文档、状态或协作规范前 | [文档职责与更新规则](docs/index.md#文档维护)；修改 Agent 入口或 Skills 规则还需读[Agent 工具维护](docs/development/agent-tooling.md#维护) |
| 采用、修改或升级 Skill 前 | [Agent 与 Skills](docs/development/agent-tooling.md)，完整阅读待使用的 SKILL.md |
| 启动、重建、停止或清理 Docker 环境前 | [Docker 开发](docs/getting-started/docker-development.md)、[Docker 磁盘治理](docs/development/docker-disk-governance.md) |
| 审查代码、文档或准备交付修改前 | [审查清单](docs/development/review-checklist.md)及实际涉及的领域文档 |
| 创建提交、操作分支或进行远端操作前 | [Git 规则](docs/development/git-rules.md) |
| 准备演示、截图或简历材料前 | [演示指南](docs/showcase/demo.md)或[简历材料](docs/showcase/resume.md)，并核对当前实现与验证证据 |

其他资料从[文档中心](docs/index.md)查找；找不到必要规则或发现实质冲突时，先核对权威来源和真实实现，
仅在无法安全判断、需要新增授权或改变重大方向时询问用户。
