# LearnPlatform · 课程学习平台

[![CI](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml/badge.svg)](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml)

LearnPlatform 是一个以**课程长期学习过程**为中心的 Web 学习平台。当前完整承载
**408 数据结构**课程：知识讲解、互动课件、练习、错题、间隔复习、阶段测评与 2026
官方真题考试共用同一条可追踪的学习闭环；其他 408 科目以结构完整的占位课程呈现。

界面遵循「安静的数字教材」原则：暖白纸面、克制的主色、以内容排版为核心，不制造
指标轰炸或能力评分。所有判分、错题状态、复习调度与权限隔离都在服务端真实完成。

## 产品定位

- **为谁设计**：正在自学并备考 408 数据结构的学生。
- **不是什么**：不是课程商城，不是通用 AI 聊天工具，不是游戏化学习应用。
- **核心体验**：登录后从「我的课程」继续；从上次停下的地方进入 AI 教学、练习、
  复习或真题考试；系统只展示真实学习事实，不伪造掌握度。

## 核心能力

- **我的课程 / 课程库**：登录默认进入「我的课程」；课程库按 408 计算机统考组织，
  Discover → Understand → Add 全流程。
- **课程空间**：继续学习、课程目录、学习工具（练习/复习/错题/真题/题目）、
  课程总体学习事实与最近测评的知识点摘要。
- **AI 教学**：已审查的分步讲解、前置补充、受限互动课件（栈、队列、双端队列、
  线性表等可视化）与服务端判分的理解检查；教学上下文会衔接你的真题、错题与
  复习记录，但只呈现事实计数。
- **练习与复习**：服务端判分、即时解析；错题进入统一课程状态；复习调度基于真实
  到期时间，并给出可解释的复习依据。
- **阶段测评**：从当前错题、到期复习与近期错误事实优先选题（数据不足时明确退回
  确定性课程题序）；提交前隐藏答案，完成后固化题源构成与逐题复盘。
- **真题考试**：2026 年 408 数据结构真题（官方来源可核验、原始题号分层）；学习
  模式逐题理解，限时考试由服务端锁定时间与作答状态，主观题按评分点由管理员
  人工批阅，客户端不能自行决定总分。
- **私有试卷**：有限结构化 Markdown / 文本、文本型 PDF 与 DOCX 导入，所有者隔离、
  原文件追溯、草稿—AI 建议—逐题人工复核—启用生命周期。
- **内容治理**：AI 生成题须经管理端审查后进入正式学习流程；官方原题与 AI 增强
  分层保存。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 后端 | Java 21、Spring Boot 3、MyBatis-Plus、Spring Security、JWT、Validation、Knife4j |
| 数据 | MySQL 8、Flyway |
| 部署 | Docker、Docker Compose、Nginx |
| 测试 | JUnit、Mockito、MockMvc、Testcontainers、Vitest、Playwright |

工程特点：学习端与管理端为独立前端应用（独立 Router、布局与构建产物，共用后端）；
Design Tokens 作为唯一视觉来源（`frontend/src/assets/styles/tokens.css`）；
学习页面使用沉浸式 Focus Layout；大型页面按领域拆分为可复用组件。

## 快速开始

### 本地开发

```bash
cp .env.example .env
source scripts/load-env.sh .env

cd backend
mvn spring-boot:run

cd ../frontend
npm ci
npm run dev
```

- 前端：`http://localhost:5173`
- 独立管理端：另运行 `npm run dev:admin` 后访问 `http://localhost:5174/admin/`
- 后端：`http://localhost:8080`
- Knife4j：`http://localhost:8080/doc.html`

完整步骤见[本地开发指南](docs/getting-started/local-development.md)。

### Docker

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

- 前端：`http://localhost`
- 独立管理端：`http://localhost/admin/`
- 后端：`http://localhost:8080`

## 演示账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 普通用户 | `testuser` | `test123` |

以上仅用于开发和演示环境。演示流程见[演示文档](docs/showcase/demo.md)。

## 质量检查

```bash
cd backend
mvn clean verify

cd ../frontend
npm run lint
npm run format:check
npm run coverage
npm run build
```

真实 MySQL Testcontainers、隔离 Docker E2E 和覆盖率门槛见[测试策略](docs/development/testing.md)。

## 项目结构

```text
LearnPlatform/
├── frontend/            # Vue 3 前端（学习端 + 独立管理端）
├── backend/             # Spring Boot 后端（Java 21）
├── docs/                # 正式项目文档
├── .agents/skills/      # 项目自有与上游安装的仓库级 Skills
├── scripts/             # 项目脚本
├── monitoring/          # 监控配置
├── docker-compose.yml
└── AGENTS.md            # Agent 长期协作入口
```

## 文档

- [文档中心](docs/index.md)
- [产品需求](docs/product/prd.md)
- [产品路线图](docs/product/roadmap.md)
- [系统架构](docs/architecture/overview.md)
- [前端架构](docs/architecture/frontend.md)
- [架构决策](docs/architecture/decisions/index.md)
- [API 参考](docs/reference/api/index.md)
- [数据库参考](docs/reference/database/index.md)
- [开发工作流](docs/development/workflow.md)
- [测试策略](docs/development/testing.md)
- [项目状态](docs/project/status.md)
- [开发日志](docs/project/changelog/index.md)
- [演示流程](docs/showcase/demo.md)
- [简历材料](docs/showcase/resume.md)

## 接口约定

- 统一响应：`{ "code": 0, "message": "success", "data": {} }`
- 普通接口：`/api/**`
- 管理接口：`/api/admin/**`

精确契约以后端代码、迁移和[API 参考](docs/reference/api/index.md)为准。

## 许可证

本项目仅供个人学习和简历展示使用。
