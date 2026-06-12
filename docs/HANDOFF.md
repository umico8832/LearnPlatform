# docs/HANDOFF.md

# AI 题库与错题复习系统 - Agent 交接文档

本文档用于跨对话、跨 Agent、跨开发阶段交接项目状态。
新 Agent 接手时，必须先阅读本文件，再结合 `AGENTS.md`、`README.md`、`docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 和实际代码判断当前状态。

---

## 1. 项目基本信息

项目名称：

AI 题库与错题复习系统

项目定位：

用于学习、刷题、错题复习和 AI 辅助学习的中大型 Web 项目。
目标是做成真实可运行、可演示、可写进简历的完整前后端分离项目。

开发环境：

* WSL2 + Ubuntu
* 项目目录位于 Linux 文件系统内，例如 `~/LearnPlatform`
* 后续所有命令以 WSL2 Ubuntu 为准

技术栈：

前端：

* Vue 3
* TypeScript
* Vite
* Element Plus
* Pinia
* Vue Router
* Axios
* ECharts

后端：

* Java 17+
* Spring Boot 3
* MyBatis-Plus
* MySQL 8
* JWT
* Knife4j / Swagger
* Validation
* 全局异常处理
* 统一响应结构

部署：

* Docker
* Docker Compose

AI 接入：

* 通过环境变量配置 API Base URL、API Key、模型名称
* 禁止写死真实密钥

---

## 2. 当前项目阶段

当前阶段：

请根据 `docs/ROADMAP.md` 和 `docs/CHANGELOG_AGENT.md` 更新此处。

阶段状态：

* [ ] Phase 0：项目规划
* [ ] Phase 1：项目骨架
* [ ] Phase 2：用户与鉴权
* [ ] Phase 3：课程与知识点
* [ ] Phase 4：题库系统
* [ ] Phase 5：刷题与判分
* [ ] Phase 6：错题本
* [ ] Phase 7：试卷与模拟考试
* [ ] Phase 8：AI 功能
* [ ] Phase 9：统计与可视化
* [ ] Phase 10：项目质量提升
* [ ] Phase 11：部署与简历材料

---

## 3. 已完成内容

请按实际代码和文档更新。

示例格式：

### 已完成模块

* 项目文档体系：

  * `docs/PRD.md`
  * `docs/ARCHITECTURE.md`
  * `docs/DB_DESIGN.md`
  * `docs/API_DESIGN.md`
  * `docs/ROADMAP.md`
  * `docs/RESUME.md`
  * `docs/CHANGELOG_AGENT.md`

### 已完成配置

* `.gitignore`
* `.env.example`
* `README.md`
* `AGENTS.md`

### 已完成前端功能

* 暂无 / 请更新

### 已完成后端功能

* 暂无 / 请更新

### 已完成联调功能

* 暂无 / 请更新

---

## 4. 未完成内容

请按实际进度更新。

示例：

* 后端 Spring Boot 3 项目骨架
* 前端 Vue 3 项目骨架
* 用户注册登录
* JWT 鉴权
* 课程管理
* 知识点管理
* 题库管理
* 刷题系统
* 错题本
* AI 解析
* Docker Compose 完整部署

---

## 5. 当前目录结构

请根据实际项目结构更新。

```text
LearnPlatform/
├── AGENTS.md
├── README.md
├── .gitignore
├── .env.example
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── DB_DESIGN.md
│   ├── API_DESIGN.md
│   ├── ROADMAP.md
│   ├── CHANGELOG_AGENT.md
│   ├── HANDOFF.md
│   └── RESUME.md
├── frontend/
└── backend/
```

---

## 6. 重要文件说明

* `AGENTS.md`：长期稳定项目规则，不记录阶段进度。
* `README.md`：项目介绍、运行方式、技术栈、演示说明。
* `docs/ROADMAP.md`：阶段计划和当前阶段。
* `docs/CHANGELOG_AGENT.md`：每轮 Agent 开发记录。
* `docs/HANDOFF.md`：跨对话交接文档。
* `docs/DB_DESIGN.md`：数据库设计。
* `docs/API_DESIGN.md`：接口设计。
* `docs/RESUME.md`：简历描述和面试材料。

---

## 7. 运行方式

请根据实际项目更新。

### 前端

```bash
cd frontend
npm install
npm run dev
```

### 后端

```bash
cd backend
./mvnw spring-boot:run
```

如果没有 `mvnw`：

```bash
mvn spring-boot:run
```

### Docker

```bash
docker compose up -d
docker compose ps
```

---

## 8. 当前遗留问题

请每轮交接时更新。

示例：

* 前端项目骨架尚未创建；
* 后端项目骨架尚未创建；
* 数据库表结构尚未落地为 SQL；
* AI 接口尚未实现；
* Docker Compose 尚未完整配置。

---

## 9. 下一步建议任务

请根据当前阶段更新。

推荐格式：

### 下一步任务

任务名称：

创建前后端项目骨架

任务目标：

* 创建后端 Spring Boot 3 项目；
* 创建前端 Vue 3 + TypeScript + Vite 项目；
* 配置基础目录结构；
* 保证前后端可以在 WSL2 Ubuntu 中启动。

涉及文件：

* `backend/`
* `frontend/`
* `README.md`
* `docs/ROADMAP.md`
* `docs/CHANGELOG_AGENT.md`

验收标准：

* 前端可以 `npm run dev` 启动；
* 后端可以启动；
* 文档记录更新；
* 不引入真实密钥。

---

## 10. 新对话续接提示词

新对话接手时，可以复制以下提示词：

```text
你现在接手一个长期开发中的全栈 Web 项目。

项目名称：
AI 题库与错题复习系统

项目定位：
这是一个用于学习、考试复习、AI 辅助刷题和简历展示的中大型 Web 项目。目标是做成真实可运行、可演示、可写进简历的完整项目，而不是玩具 Demo。

开发环境：
WSL2 + Ubuntu。后续所有开发、命令执行、路径、脚本都以 WSL2 Ubuntu 为准。不要使用 PowerShell 或 Windows 路径。

技术栈：
前端：Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + Axios + ECharts
后端：Spring Boot 3 + Java 17 + MyBatis-Plus + MySQL + JWT + Knife4j / Swagger
部署：Docker Compose
AI：通过环境变量配置 API Base URL、API Key、模型名称，不能写死真实密钥。

请先阅读以下文件：
1. AGENTS.md
2. README.md
3. docs/ROADMAP.md
4. docs/CHANGELOG_AGENT.md
5. docs/HANDOFF.md
6. docs/PRD.md
7. docs/ARCHITECTURE.md
8. docs/DB_DESIGN.md
9. docs/API_DESIGN.md
10. docs/RESUME.md

工作方式：
1. 先根据 docs/HANDOFF.md 理解当前项目状态；
2. 再根据 docs/ROADMAP.md 判断当前阶段；
3. 再根据代码实际情况验证文档是否过时；
4. 不要盲目相信旧总结，必须检查代码；
5. 自动选择下一步最高优先级任务；
6. 继续开发、测试、修复、更新文档；
7. 除非遇到重大方向问题，否则不要频繁问我；
8. 每轮结束都要更新 docs/CHANGELOG_AGENT.md 和必要文档；
9. 上下文接近 65 万 token 时准备交接，约 70 万 token 时必须生成新的 docs/HANDOFF.md 和新对话续接提示词。

请先执行：
1. 阅读项目状态；
2. 总结当前完成情况；
3. 判断下一步任务；
4. 然后直接开始推进下一阶段开发。
```

---

## 11. Git 状态建议

每完成一个阶段建议提交一次。

当前建议 commit message：

```text
docs(project): 完善 Agent 长期协作规则与交接文档
```

---

## 12. 交接注意事项

* 不要依赖旧对话记忆；
* 不要把 `AGENTS.md` 当进度表；
* 不要清空 `docs/CHANGELOG_AGENT.md`；
* 不要覆盖真实 `.env`；
* 不要提交真实 API Key；
* 先检查代码，再相信文档；
* 发现文档与代码不一致时，应以代码为准，并修正文档。
