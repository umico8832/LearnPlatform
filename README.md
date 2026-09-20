# LearnPlatform · 课程学习平台

[![CI](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml/badge.svg)](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml)

LearnPlatform 是一个以**理解知识**和**课程长期学习过程**为中心的 Web AI 学习平台。
教学 Agent 围绕课程结构与真实学习记录组织讲解、例子、互动可视化和理解检查，帮助学习者
把难懂的知识逐步弄明白，而不只是得到一次答案。

当前完整演示以 **408 数据结构**为课程案例：知识讲解、互动课件、练习、错题、间隔复习、
阶段测评与 2026 官方真题考试共用同一条可追踪的学习闭环；它用于验证通用教学平台的核心能力，
不代表产品只面向 408。其他课程仍处于待扩展状态。

界面遵循「安静的数字教材」原则：暖白纸面、克制的主色、以内容排版为核心，不制造
指标轰炸或能力评分。所有判分、错题状态、复习调度与权限隔离都在服务端真实完成。

## 产品定位

- **为谁设计**：希望在持续对话、可视化和练习中真正理解一门课程的自学者。
- **不是什么**：不是一次性给答案的通用聊天工具，也不是用积分和指标替代学习过程的应用。
- **核心体验**：从「我的课程」继续，在教学 Agent 的讲解、练习、复习和测评之间保持连续；
  系统只使用真实学习事实，不伪造掌握度。

## 核心能力

- **我的课程 / 课程库**：登录默认进入「我的课程」；课程库按 408 计算机统考组织，
  Discover → Understand → Add 全流程。
- **课程空间**：继续学习、课程目录、学习工具（练习/复习/错题/真题/题目）、
  课程总体学习事实与最近测评的知识点摘要。
- **AI 教学**：已审查的分步讲解、前置补充、受限互动课件（栈、队列、双端队列、
  线性表等可视化）与服务端判分的理解检查；教学上下文会衔接你的真题、错题与
  复习记录，但只呈现事实计数。显式启用支持工具调用的云模型后，还可在同一教学
  会话中追问，并在刷新后恢复已完成的对话轮次。
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
test -f .env || cp .env.example .env
# 在本机填写 .env 后，启动 Docker 基础服务
python3 scripts/dev.py infra-up
# 终端一：本地后端
python3 scripts/dev.py backend
# 终端二：本地前端（npm ci 仅首次安装或依赖改变时需要）
npm --prefix frontend ci
python3 scripts/dev.py frontend
```

- 前端：`http://localhost:5173`
- 独立管理端：另运行 `python3 scripts/dev.py admin` 后访问 `http://localhost:5174/admin/`
- 后端：`http://localhost:8080`
- Knife4j：`http://localhost:8080/doc.html`

完整步骤见[本地开发指南](docs/getting-started/local-development.md)。

### Docker

```bash
test -f .env || cp .env.example .env
python3 scripts/docker-lifecycle.py app-up
```

- 前端：`http://localhost`
- 独立管理端：`http://localhost/admin/`
- 后端：`http://localhost:8080`

脚本使用当前工作区构建前后端，验证新容器健康后替换旧版本并安全回收悬空镜像；实际端口以
`.env` 和命令输出为准。镜像与源码状态可用 `python3 scripts/docker-lifecycle.py app-status`
检查。完整规则见[Docker 开发指南](docs/getting-started/docker-development.md)。

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
- [项目里程碑](docs/project/history.md)
- [演示流程](docs/showcase/demo.md)
- [简历材料](docs/showcase/resume.md)

## 接口约定

- 统一响应：`{ "code": 0, "message": "success", "data": {} }`
- 普通接口：`/api/**`
- 管理接口：`/api/admin/**`

精确契约以后端代码、迁移和[API 参考](docs/reference/api/index.md)为准。

## 许可证

本项目仅供个人学习和简历展示使用。
