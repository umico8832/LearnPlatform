# AGENTS.md

本文件是本项目 AI Agent 的长期项目规则。
后续无论使用 Codex、Cline、MiMo 或其他 Agent，都必须先阅读并遵守本文件。

---

## 1. 项目定位

本项目是一个用于学习、刷题、错题复习和 AI 辅助学习的中大型 Web 项目。

项目名称暂定：

**AI 题库与错题复习系统**

项目目标：

* 真实可运行；
* 可持续扩展；
* 可用于简历展示；
* 前后端分离；
* 有完整业务闭环；
* 有清晰文档、部署方式和演示流程。

开发过程中必须优先保证项目能运行、能演示、能解释。

---

## 2. 开发环境

本项目统一使用：

macOS 26
---

## 3. 技术栈

### 前端

* Vue 3
* TypeScript
* Vite
* Element Plus
* Pinia
* Vue Router
* Axios
* ECharts
* Markdown 渲染组件

### 后端

* Java 17+
* Spring Boot 3
* MyBatis-Plus
* MySQL 8
* JWT
* Knife4j / Swagger
* Validation
* 全局异常处理
* 统一响应结构

### 部署

* Docker
* Docker Compose
* `.env.example`

### AI 接入

* 通过环境变量配置 API Base URL、API Key、模型名称
* 禁止写死真实密钥
* 禁止把真实 API Key 写入代码、README、测试数据或 Git 历史

---

## 4. Agent 工作方式

Agent 需要尽可能自主推进项目。

每轮开发前必须：

1. 阅读本文件；
2. 阅读 `README.md`；
3. 阅读 `docs/ROADMAP.md`；
4. 阅读 `docs/CHANGELOG_AGENT.md`；
5. 必要时阅读 `docs/HANDOFF.md`；
6. 判断当前项目阶段；
7. 自动选择下一步最重要任务。

除非遇到重大方向问题，否则不要频繁询问用户。

小问题自行做合理默认决策，并在总结中说明。

---

## 5. 每轮开发流程

实现功能、修复问题或重构代码的开发轮次，必须按以下流程执行：

1. 判断当前项目状态；
2. 明确本轮目标；
3. 明确修改边界；
4. 修改代码；
5. 运行必要检查；
6. 修复发现的问题；
7. 更新相关文档；
8. 输出本轮总结；
9. 给出下一轮建议；
10. 给出建议 commit message。

如果用户明确要求只读分析、代码审查、解释、调研或不修改文件，则不得为了满足流程而强行修改代码或文档。此类轮次应完成必要检查，并明确说明未修改文件。

用户当前轮次的明确要求优先于“自动选择下一步任务”。当用户已经指定目标或修改边界时，只处理该目标，不擅自推进 ROADMAP 中的其他任务。

每轮总结必须包含：

* 本轮完成内容；
* 修改文件；
* 如何运行；
* 如何验证；
* 遗留问题；
* 下一轮建议；
* 建议 commit message。

---

## 6. 必须维护的文档

以下文档必须持续维护：

```text
README.md
docs/PRD.md
docs/ARCHITECTURE.md
docs/DB_DESIGN.md
docs/API_DESIGN.md
docs/ROADMAP.md
docs/CHANGELOG_AGENT.md
docs/HANDOFF.md
docs/RESUME.md
```

要求：

* 功能完成后必须更新 `docs/CHANGELOG_AGENT.md`；
* 阶段变化后必须更新 `docs/ROADMAP.md`；
* 数据库结构变化后必须更新 `docs/DB_DESIGN.md`；
* 接口变化后必须更新 `docs/API_DESIGN.md`；
* 架构变化后必须更新 `docs/ARCHITECTURE.md`；
* 进入上下文转接点时必须更新 `docs/HANDOFF.md`；
* 简历描述必须和真实项目一致，不能夸大。

---

## 7. 项目状态来源与维护边界

`AGENTS.md` 只保存长期稳定的项目规则，不用于记录当前开发阶段、已完成任务或下一步计划。

当前项目状态必须以以下文件为准：

```text
docs/ROADMAP.md
docs/CHANGELOG_AGENT.md
docs/HANDOFF.md
```

各文件职责如下：

* `docs/ROADMAP.md`：记录项目阶段规划、当前阶段、已完成阶段、下一阶段任务；
* `docs/CHANGELOG_AGENT.md`：记录每轮 Agent 开发内容、修改文件、验证结果、遗留问题；
* `docs/HANDOFF.md`：记录跨对话交接信息，用于新 Agent 无缝接手。

Agent 每轮开发前必须优先阅读：

```text
AGENTS.md
README.md
docs/ROADMAP.md
docs/CHANGELOG_AGENT.md
docs/HANDOFF.md
```

其中：

* `AGENTS.md` 用于理解长期规则；
* `ROADMAP.md` 用于判断当前阶段；
* `CHANGELOG_AGENT.md` 用于了解最近开发记录；
* `HANDOFF.md` 用于跨对话续接。

除非项目长期规则发生变化，否则不要修改 `AGENTS.md`。

以下情况可以修改 `AGENTS.md`：

* 开发环境发生长期变化；
* 技术栈发生长期变化；
* Git 提交规范变化；
* 安全规则变化；
* Agent 工作流规则变化；
* 文档维护规则变化。

以下情况禁止修改 `AGENTS.md`，应改写对应文档：

* 当前完成了哪个阶段；
* 下一轮准备做什么；
* 某个 bug 是否修复；
* 某个功能是否完成；
* 某次开发修改了哪些文件；
* 当前遗留问题；
* 临时 TODO。

这些动态内容必须写入：

```text
docs/ROADMAP.md
docs/CHANGELOG_AGENT.md
docs/HANDOFF.md
```

---

## 8. 上下文转接规则

如果当前对话上下文过长，或者 Agent 判断接近上下文上限，应主动停止继续开发，并生成交接材料。

当上下文达到约 70 万左右 token 时，建议暂停开发，生成交接文档。

交接时必须更新：

```text
docs/HANDOFF.md
docs/CHANGELOG_AGENT.md
```

并输出一份可复制到新对话的续接提示词。

续接提示词必须包含：

* 当前项目阶段；
* 已完成内容；
* 未完成内容；
* 当前技术栈；
* 重要文件；
* 运行方式；
* 遗留问题；
* 下一步任务；
* 新 Agent 应该先阅读哪些文件。

---

## 9. 代码质量要求

禁止：

* 只写计划不落地；
* 只写伪代码；
* 前端长期使用假数据不接真实接口；
* 后端接口写完但前端不联调；
* 文档声称完成但代码没有实现；
* 引入无必要的复杂架构；
* 把真实密钥写进项目；
* 大范围重构但不说明原因；
* 混合多个无关任务到一次提交。

要求：

* 前后端字段保持一致；
* 后端接口使用统一响应结构；
* 后端异常统一处理；
* 数据库字段命名使用 `snake_case`；
* Java 字段命名使用 `camelCase`；
* 重要表包含 `create_time`、`update_time`、`deleted`；
* 新增依赖必须说明原因；
* 优先保证项目可运行。

---

## 10. 前端设计规则

默认 UI 风格：

* 简洁；
* 现代；
* 偏 SaaS 后台；
* 不要大面积渐变；
* 不要过度“AI 味”；
* 不要花哨堆效果；
* 优先保证可读性和一致性。

需要进行前端页面美化、布局优化、交互优化时，必须先阅读：

```text
skills/frontend-design/SKILL.md
```

如果该文件不存在，应先说明缺失，不要假装已经参考。

---

## 11. 安全规则

禁止读取、输出、提交以下内容：

```text
.env
真实 API Key
真实 Token
真实 Cookie
真实数据库密码
真实个人隐私数据
```

允许创建和维护：

```text
.env.example
```

`.env.example` 只能包含示例值，例如：

```env
AI_API_KEY=your_api_key_here
JWT_SECRET=change_me
```

---

## 12. Git 提交规范

当用户要求提交 git commit 时，必须使用以下提交信息规范。

格式：

```text
<type>(<可选作用域>): <中文简短描述>

<可选: 详细说明>
<可选: Closes #42>
```

大多数提交一句话搞定。
一行不够时，空一行再补充。

type 只能使用：

* `feat`：新功能
* `fix`：修 bug
* `docs`：文档
* `style`：代码格式，不影响逻辑
* `refactor`：重构
* `perf`：性能优化
* `test`：测试
* `chore`：构建、工具、依赖
* `ci`：CI/CD
* `security`：安全

要求：

* 提交信息必须使用中文描述；
* 一次提交只描述一件事；
* 修改涉及特定模块时优先添加作用域；
* 禁止使用 `fix bug`、`wip`、`修改了一些文件`；
* 禁止写成 `feat: 添加A顺便修了B` 这类混合事项描述。

示例：

```text
feat(auth): 完成 JWT 登录注册流程
```

```text
docs(roadmap): 更新项目阶段进度
```

```text
fix(question): 修复题目选项保存失败问题
```

---

## 13. 默认命令约定

如果项目结构已经创建，优先使用以下命令。

前端：

```bash
cd frontend
npm install
npm run dev
npm run build
```

后端：

```bash
cd backend
./mvnw spring-boot:run
```

如果没有 `mvnw`，可以使用：

```bash
mvn spring-boot:run
```

Docker：

```bash
docker compose up -d
docker compose ps
docker compose logs -f
```

如果命令失败，必须先分析错误原因，再修改。

不要盲目重复执行同一个失败命令。
