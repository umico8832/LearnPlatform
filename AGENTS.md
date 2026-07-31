# LearnPlatform Agent 协作规则

本文件是 LearnPlatform 的长期协作入口。它只记录稳定规则和文档路由；当前阶段、测试数字、临时 TODO 和每轮结果写入对应动态文档。

项目用于个人本地学习与简历展示。回答和决策应客观、理性、全面，不因迎合用户而忽略事实、风险或可行性。

## 1. 项目目标

LearnPlatform 是一个用于刷题、错题复习、考试和 AI 辅助学习的前后端分离 Web 项目。

开发优先级：

1. 真实可运行；
2. 业务闭环完整；
3. 可持续扩展；
4. 可测试、可演示、可解释；
5. 文档和实现一致。

## 2. 通用原则

- 用户当前轮次的明确要求优先。
- 只处理用户授权范围，不擅自扩展到无关任务。
- 小问题可以作合理默认决定并在总结中说明；重大方向、破坏性操作或需求冲突必须暂停确认。
- 新增功能、修复、重构或文档规则变化后，同步必要文档。
- 只读分析、审查或调研不得为了流程强行修改文件。
- 实现完成后按风险验证，并在工作区没有不相关改动时创建一次聚焦提交。
- 每轮说明完成内容、修改文件、验证、遗留问题、下一步和 commit 状态。

## 3. 默认读取

新对话或新 Agent 接手时先读：

1. `AGENTS.md`
2. `docs/project/status.md`
3. `docs/product/roadmap.md`
4. `README.md`

仅在明确进行上下文交接时读取：

- `docs/project/handoff.md`

仅在追溯历史时读取：

- `docs/project/changelog/index.md`
- 与目标相关的月份和 Round

不要默认全文读取所有历史日志。

## 4. 按任务读取

- 产品范围：`docs/product/prd.md`
- 远期规划：`docs/product/future.md`
- AI 学习平台战略：`docs/product/ai-learning-platform-strategy.md`
- 架构和模块边界：`docs/architecture/overview.md`
- 重大架构决策：`docs/architecture/decisions/`
- API：`docs/reference/api/index.md`
- 数据库或迁移：`docs/reference/database/index.md`
- 新增功能、修复、重构：`docs/development/workflow.md`
- 工程归属：`docs/development/engineering-rules.md`
- 测试策略：`docs/development/testing.md`
- Git 和提交：`docs/development/git-rules.md`
- 审查：`docs/development/review-checklist.md`
- Agent 与 Skills：`docs/development/agent-tooling.md`
- 演示和截图：`docs/showcase/demo.md`
- 简历：`docs/showcase/resume.md`

前端美化、布局或交互优化时：

1. 读取 `.agents/skills/ui-ux-pro-max/SKILL.md` 获取通用 UX 和设计判断；
2. 读取 `.agents/skills/frontend-design/SKILL.md` 获取项目落地约束；
3. 检查真实 Vue 3、Element Plus 和现有设计变量；
4. 只有用户明确要求 shadcn/ui、Radix 或 Tailwind 时才使用 `.agents/skills/ui-styling/SKILL.md` 的实现路径。

临时浏览器流程验收读取 `.agents/skills/frontend-flow-test/SKILL.md`，并结合 `docs/development/testing.md`。

## 5. 状态与文档权威来源

- 当前阶段、最新验证、遗留问题、下一步：`docs/project/status.md`
- 长期规划：`docs/product/roadmap.md`
- 每轮历史：`docs/project/changelog/`
- 产品范围：`docs/product/prd.md`
- 架构：`docs/architecture/`
- API：`docs/reference/api/index.md`
- 数据库：`docs/reference/database/index.md`

README、HANDOFF 和 ROADMAP 不复制 STATUS 中的动态细节。

更新原则：

- 功能或规则完成后更新当前月份 changelog。
- 当前阶段、最新验证、遗留问题或下一步变化时更新 status。
- 阶段目标变化时更新 roadmap。
- 接口、数据库或架构变化时更新对应权威文档。
- 进入上下文转接点时更新 handoff。
- 简历和演示只能陈述真实能力。

## 6. Skills 所有权

- `.agents/skills/` 是当前 Codex 仓库级标准目录，同时容纳 LearnPlatform 自有工作流和上游安装的通用 Skill。
- `context-handoff`、`frontend-design`、`frontend-flow-test` 由 LearnPlatform 维护。
- `banner-design`、`brand`、`design`、`design-system`、`slides`、`ui-styling`、`ui-ux-pro-max` 由 `ui-ux-pro-max-cli` 按上游默认方式生成。
- 不直接修改、移动或重写上游生成的 Skill；升级时执行 `npm install -g ui-ux-pro-max-cli@latest` 和 `uipro init --ai codex --force`。
- 使用不熟悉的 Skill 前必须完整阅读对应 `SKILL.md`，不得根据名称猜测。
- 第三方 Skill 引用的依赖不存在时必须说明，不得假装完成相关步骤。
- Skill 与本文件或用户要求冲突时，以用户要求和本文件为准。

## 7. 开发与测试底线

禁止：

- 用伪代码、假数据、硬编码或临时代码冒充完成；
- 文档声称完成但代码未实现；
- 后端完成后长期不接前端真实接口；
- 为测试通过弱化权限、判分、安全或一致性规则；
- 引入无必要复杂架构；
- 在一次改动中混合无关任务；
- 对 `docs/development/testing.md` 规定的高风险行为，在没有有效失败用例时直接实现且不说明例外。

涉及行为变化时遵循 `docs/development/workflow.md` 的发现、契约、Red、Green、Refactor、Verify 和 Deliver。纯文档与机械重命名无需制造 Red，但必须执行相称的链接、结构和一致性验证。

## 8. 安全与 Git

- 禁止读取、输出或提交 `.env`、真实 API Key、Token、Cookie、数据库密码和隐私数据。
- `.env.example` 只能包含示例值。
- 不覆盖用户已有改动。
- 禁止未经确认执行 push、rebase、reset、force push、清理历史或删除分支。
- 工作区存在不相关未提交改动时，只有能够可靠隔离本轮文件或代码块且不依赖其他改动，才可只暂存本轮范围；否则不得自动 commit。
- 提交必须聚焦并符合 `docs/development/git-rules.md`。

## 9. 上下文转接

上下文接近上限或用户要求交接时：

1. 更新 `docs/project/status.md`；
2. 更新 `docs/project/handoff.md`；
3. 必要时追加当前月份 changelog；
4. 提供可复制的最小续接提示词。

不要把完整历史重新复制进 handoff。
