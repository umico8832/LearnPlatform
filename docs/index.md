# 文档中心

本目录是 LearnPlatform 的正式知识入口。文档与代码一起版本化、评审和验证；同一事实只保留一个权威来源，其他文档通过链接引用。

## 快速入口

- 第一次运行项目：[本地开发](getting-started/local-development.md) 或 [Docker 开发](getting-started/docker-development.md)
- 配置与排错：[配置说明](getting-started/configuration.md)和[常见问题排查](getting-started/troubleshooting.md)
- 了解产品：[产品需求](product/prd.md)、[产品路线图](product/roadmap.md)、[后续扩展方向](product/future.md)
- 了解系统：[系统架构](architecture/overview.md)
- 查询契约：[API 参考](reference/api/index.md) 与 [数据库参考](reference/database/index.md)
- 开始开发：[AI Agent 开发工作流](development/workflow.md) 与 [测试策略](development/testing.md)
- 工程协作：[Git 规则](development/git-rules.md)与[审查标准](development/workflow.md#审查标准)
- Docker 磁盘治理：[Docker 磁盘增长治理](development/docker-disk-governance.md)
- 查看当前状态：[项目状态](project/status.md)
- 查看历史：[开发日志索引](project/changelog/index.md)
- 查看历史体检：[工程审计归档](project/audits/index.md)
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
| `project/` | 当前状态和历史日志 | 当前事实与历史记录严格分离 |
| `showcase/` | 演示、简历和截图 | 只能陈述真实已实现能力 |

## 权威来源

| 事实 | 权威文档 |
|---|---|
| 当前阶段、最新验证、遗留问题、下一步 | [project/status.md](project/status.md) |
| 长期阶段规划 | [product/roadmap.md](product/roadmap.md) |
| 尚未完成的候选方向与进入条件 | [product/future.md](product/future.md) |
| 每轮修改和验证历史 | [project/changelog/](project/changelog/index.md) |
| 产品范围与稳定验收标准 | [product/prd.md](product/prd.md) |
| 模块入口、依赖方向与职责边界 | [architecture/overview.md](architecture/overview.md)及对应前后端文档 |
| 跨模块处理过程 | [architecture/data-flow.md](architecture/data-flow.md) |
| 重要架构决策 | [architecture/decisions/](architecture/decisions/index.md) |
| 请求、响应、权限与调用者可见行为 | [reference/api/index.md](reference/api/index.md)及对应业务域 |
| 存储语义、唯一约束与事务不变量 | [reference/database/index.md](reference/database/index.md)及对应业务域 |
| 验证范围、命令与 E2E 生命周期 | [development/testing.md](development/testing.md) |
| 开发循环、执行模式与完成状态 | [development/workflow.md](development/workflow.md) |
| 提交边界、分支与远端操作 | [development/git-rules.md](development/git-rules.md) |
| 项目 Skills 来源与升级 | 本页的[工具维护](#工具维护) |
| 文档归属、更新与验证规则 | 本页的[文档维护](#文档维护) |

API 字段、数据库结构、权限和部署事实以各领域参考文档指向的代码、类型、迁移和配置为准。
文档与实现不一致时，先区分需求约定和当前能力；不得根据一份过期文档直接改变业务行为。
同一规则重复或冲突时在权威来源中核对并合并；重大产品方向或授权冲突无法消解时询问用户。
跨文档可以保留理解所需的简短摘要，但完整细则只在所属位置维护：数据流引用契约，演示引用环境步骤，
roadmap 引用有效候选。不能因为某个流程涉及多个领域，就在每处重写全部规则。

## 文档维护

修改文档、状态或协作规范前，先确定本次事实归属；仅更新事实变化的权威文档，其他位置通过链接引用。

- README 承担项目入口；README 和 roadmap 不复制 status 中的动态验证与当前待办。
- 功能或规则完成后更新当前月份 changelog；阶段、接口、数据库或架构变化时更新对应权威文档。
  动态状态变化时才更新 status，不机械同步所有文档；只读问答、审查和调研不强行产生记录。
- status 保持当前快照：验证按类别替换为最近结果，注明适用范围与后续未重验改动；不按轮次追加实现摘要。
  已解决问题和已退出阶段的过程留在 changelog，status 仅通过历史链接保留必要证据。
- 简历、演示与能力介绍只能陈述真实实现及其限制；候选方向实现后从 future 移出，当前状态与历史分开记录。

写入长期规则前，必须确认它会反复影响后续任务、不是已有规则的重复，且缺失时确实可能导致错误决策。
一次故障、工具版本、测试数量或临时兼容办法不升级为长期规则：当前事实写入 status，单次事实写入 changelog。
能够由代码、配置或脚本强制的约束优先在对应工具中维护，文档只说明必要的使用边界与入口。
迁移最新版本、完整类清单等机械信息直接查源码；文档保留重要语义、约束和取舍，不维护第二份手工清单。

根 AGENTS.md 只保留必要底线和任务路由，领域流程由对应文档或项目 Skill 维护。路由应说明触发条件和
读取目标；不重复维护 Skill 的触发清单，不为目录对称新增文件。修改规则时搜索并消除相邻重复和冲突，
优先删除失效内容，不通过压缩长行掩盖膨胀。

### 文档验证

- 修改后运行 `python3 scripts/check-docs.py`，检查链接、导航、结构、职责、API、数据库清单与仓库 Skills。
- 检查器还限制 status 的内容预算和轮次记录；通过检查不代表历史验证仍适用于当前代码。
- 纯文档与机械重命名无需制造失败测试，按影响核对链接、结构和语义一致性。
  仅调整规则措辞或路由时，用代表性任务检查读取路径、适用范围和规则归属，不为每句话增加测试。
- 验证范围、未执行项与提交状态据实报告；验证脚本本身发生行为变化时，遵循[测试策略](development/testing.md)。

## 工具维护

- `frontend-design`、`frontend-flow-test` 是项目自有 Skill，工作流维护在各自 `SKILL.md`；项目事实通过链接引用 docs。
- `banner-design`、`brand`、`design`、`design-system`、`slides`、`ui-styling`、`ui-ux-pro-max` 来自
  [ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)，不直接修改、移动或格式化生成内容。
  项目约束写入自有 Skill；上游目录名仅标记所有权，不代表项目必须安装全套技能。
- 升级上游包前确认工作区可隔离，执行 `npm install -g ui-ux-pro-max-cli@latest` 和
  `uipro init --ai codex --force`，检查生成差异及自有 Skill 是否被覆盖；不得将升级与无关任务混合。
- 修改自有 Skill 后执行系统 `skill-creator` 的 `quick_validate.py`，核对描述、引用与适用范围；
  安装或升级后运行 `python3 scripts/check-docs.py`。版本和单次验证记入 changelog。
- 文档门禁检查已安装 Skill 的发现元数据及自有文档链接，不强制固定安装清单或 `agents/openai.yaml`。
  新增上游包时注明来源，并在检查器的上游清单登记，以免按项目文档格式重写上游内容。

Codex 的技能发现、按需加载和可选元数据以[官方 Skills 文档](https://learn.chatgpt.com/docs/build-skills)为准；
指令发现以[官方 AGENTS.md 文档](https://learn.chatgpt.com/docs/agent-configuration/agents-md)为准。
