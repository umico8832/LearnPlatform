# AGENTS.md

本文件是 LearnPlatform 项目 AI Agent 的长期协作入口。无论使用 Codex、Cline、Mimo、GPT 或其他 Agent，都应先阅读并遵守本文件。

`AGENTS.md` 只记录长期稳定规则、文档读取路由和少量硬底线；当前阶段、已完成内容、下一步计划和临时 TODO 不写入本文件。

---

## 1. 项目定位

本项目是一个用于学习、刷题、错题复习和 AI 辅助学习的中大型 Web 项目。

项目名称暂定：**AI 题库与错题复习系统**

项目目标：

- 真实可运行；
- 可持续扩展；
- 可用于简历展示；
- 前后端分离；
- 有完整业务闭环；
- 有清晰文档、部署方式和演示流程。

开发过程中优先保证项目能运行、能演示、能解释。

---

## 2. 通用工作原则

- 用户当前轮次的明确要求优先于自动推进路线图。
- 当用户已经指定目标、边界或只读要求时，只处理该目标，不擅自扩展到无关任务。
- 如果用户只说“继续开发”，Agent 应结合 `docs/HANDOFF.md`、`docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 和真实代码状态，选择下一步最重要任务。
- 小问题可以做合理默认决策，并在总结中说明；重大方向、破坏性操作或需求冲突必须暂停确认。
- 实现功能、修复问题或重构时，应同步更新相关文档；只读分析、审查、解释或调研轮次不得为了满足流程而强行修改文件。
- 每轮结束应说明完成内容、修改文件、验证方式、遗留问题、下一步建议和建议 commit message。

---

## 3. 文档读取路由

新 Agent 接手或新对话开始时，先读：

- `AGENTS.md`
- `docs/HANDOFF.md`
- `docs/ROADMAP.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

按任务类型继续读取：

- 新增功能、重构、代码归属不清：`docs/ENGINEERING_RULES.md`
- 涉及测试策略、是否补测试、如何验证：`docs/TESTING.md`
- 涉及接口变化：`docs/API_DESIGN.md`
- 涉及数据库结构或迁移：`docs/DB_DESIGN.md`
- 涉及架构、目录、模块边界：`docs/ARCHITECTURE.md`，必要时读 `docs/ENGINEERING_RULES.md`
- 涉及 commit、分支、回滚、Git 历史：`docs/AGENT_GIT_RULES.md`
- 审查、复盘、判断能否 commit：`docs/AGENT_REVIEW_CHECKLIST.md`
- 涉及演示、截图、展示流程：`docs/DEMO.md`
- 涉及简历表述：`docs/RESUME.md`
- 涉及远期规划或候选功能：`docs/FUTURE.md`
- 涉及 AI 学习平台战略、AI 长期能力地图、AI 功能优先级：`docs/AI_LEARNING_PLATFORM_STRATEGY.md`
- 涉及产品范围和业务定义：`docs/PRD.md`

前端页面美化、布局优化、交互优化时，如果存在 `skills/frontend-design/SKILL.md`，应先阅读；如果不存在，应明确说明缺失，不要假装已经参考。

---

## 4. 项目状态来源

当前项目状态以以下文件为准：

- `docs/ROADMAP.md`：项目阶段规划、当前阶段、已完成阶段、下一阶段任务；
- `docs/CHANGELOG_AGENT.md`：每轮 Agent 开发内容、修改文件、验证结果、遗留问题；
- `docs/HANDOFF.md`：跨对话交接信息，用于新 Agent 无缝接手。

除非长期规则发生变化，否则不要修改 `AGENTS.md`。以下动态内容应写入 `docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 或 `docs/HANDOFF.md`：

- 当前完成了哪个阶段；
- 下一轮准备做什么；
- 某个 bug 是否修复；
- 某个功能是否完成；
- 某次开发修改了哪些文件；
- 当前遗留问题；
- 临时 TODO。

---

## 5. 硬底线

禁止：

- 只写计划不落地；
- 用伪代码、假数据、硬编码或临时代码冒充功能完成；
- 文档声称完成但代码没有实现；
- 前端长期使用假数据不接真实接口；
- 后端接口写完但前端不联调；
- 为了测试通过而弱化业务规则、权限规则或安全约束；
- 引入无必要的复杂架构；
- 混合多个无关任务到一次提交；
- 大范围重构但不说明原因。

安全规则：

- 禁止读取、输出、提交 `.env`、真实 API Key、真实 Token、真实 Cookie、真实数据库密码或真实个人隐私数据。
- 允许创建和维护 `.env.example`，但只能包含示例值，例如 `AI_API_KEY=your_api_key_here`、`JWT_SECRET=change_me`。
- 禁止把真实 API Key 写入代码、README、测试数据或 Git 历史。

---

## 6. 必须持续维护的文档

以下文档需要随项目变化持续维护：

- `README.md`
- `docs/PRD.md`
- `docs/ARCHITECTURE.md`
- `docs/DB_DESIGN.md`
- `docs/API_DESIGN.md`
- `docs/ROADMAP.md`
- `docs/CHANGELOG_AGENT.md`
- `docs/HANDOFF.md`
- `docs/RESUME.md`

更新原则：

- 功能完成后更新 `docs/CHANGELOG_AGENT.md`；
- 阶段变化后更新 `docs/ROADMAP.md`；
- 数据库结构变化后更新 `docs/DB_DESIGN.md`；
- 接口变化后更新 `docs/API_DESIGN.md`；
- 架构变化后更新 `docs/ARCHITECTURE.md`；
- 进入上下文转接点时更新 `docs/HANDOFF.md`；
- 简历描述必须和真实项目一致，不能夸大。

---

## 7. 上下文转接

如果当前对话上下文过长，或 Agent 判断接近上下文上限，应暂停继续开发并生成交接材料。

交接时必须更新：

- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

交接输出应包含可复制到新对话的续接提示词，并说明当前项目阶段、已完成内容、未完成内容、运行方式、遗留问题、下一步任务和新 Agent 应先阅读哪些文件。
