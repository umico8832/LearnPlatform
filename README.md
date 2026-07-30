# AI 题库与错题复习系统

[![CI](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml/badge.svg)](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml)

LearnPlatform 是一套面向学习者和备考人群的前后端分离刷题平台，提供题库、练习判分、错题复习、模拟考试、学习诊断和 AI 辅助学习能力。

## 核心能力

- 课程、知识点和多题型题库管理。
- 顺序、随机、课程和知识点练习，提交后真实判分。
- 错题自动收集、掌握状态、重练和间隔重复。
- 试卷组卷、倒计时考试、自动判分和结果分析。
- AI 流式解析、复习建议、学习资产和结构化变式题。
- 学习报告、薄弱知识点、全局搜索和个性化建议。
- 题目投稿、AI 质检、纠错反馈、复审和版本记录。
- AI Token、成本、配额、审计、提醒和观察性学习效果分析。
- 管理端课程、题目、试卷、用户、投稿和 AI 运营管理。

当前阶段和最新验证见[项目状态](docs/project/status.md)。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 后端 | Java 17+、Spring Boot 3、MyBatis-Plus、Spring Security、JWT、Validation、Knife4j |
| 数据 | MySQL 8、Flyway |
| 部署 | Docker、Docker Compose、Nginx |
| 测试 | JUnit、Mockito、MockMvc、Testcontainers、Vitest、Playwright |

项目已移除 Lombok，Java 实体使用手写 getter、setter 和 `toString`。

## 快速开始

### 本地开发

```bash
cp .env.example .env
source scripts/load-env.sh .env

cd backend
mvn spring-boot:run

cd ../frontend
npm install
npm run dev
```

- 前端：`http://localhost:5173`
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
- 后端：`http://localhost:8080`

端口调整、镜像重建和停止方式见[Docker 开发指南](docs/getting-started/docker-development.md)。

## 演示账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 普通用户 | `testuser` | `test123` |

以上仅用于开发和演示环境。

演示流程和真实截图见[演示文档](docs/showcase/demo.md)。

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
├── frontend/            # Vue 3 前端
├── backend/             # Spring Boot 后端
├── docs/                # 正式项目文档
├── skills/              # LearnPlatform 项目 Skills
├── .codex/skills/       # 第三方开源 Skills 包
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
- [架构决策](docs/architecture/decisions/index.md)
- [API 参考](docs/reference/api.md)
- [数据库参考](docs/reference/database.md)
- [开发工作流](docs/development/workflow.md)
- [测试策略](docs/development/testing.md)
- [Agent 与 Skills](docs/development/agent-tooling.md)
- [项目状态](docs/project/status.md)
- [开发日志](docs/project/changelog/index.md)
- [演示流程](docs/showcase/demo.md)
- [简历材料](docs/showcase/resume.md)

## 接口约定

- 统一响应：`{ "code": 0, "message": "success", "data": {} }`
- 普通接口：`/api/**`
- 管理接口：`/api/admin/**`

精确契约以后端代码、迁移和[参考文档](docs/reference/api.md)为准。

## 许可证

本项目仅供个人学习和简历展示使用。
