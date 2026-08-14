# LearnPlatform AI 课程学习平台

[![CI](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml/badge.svg)](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml)

LearnPlatform 是一个 Web 优先的 AI 课程学习平台。用户将课程加入个人课程库后，可以
从 AI 教学或试卷学习进入同一条长期学习过程，持续使用知识讲解、互动课件、练习、
错题、复习和测评。现有题库、判分、考试与内容治理能力是这条学习闭环的业务底座。

当前仓库已经具备成熟的刷题、考试和 AI 题目学习基础，正在进入课程学习平台转型阶段；
目标范围与真实进度分别见[产品需求](docs/product/prd.md)和[项目状态](docs/project/status.md)。

## 产品结构

- 课程商店与个人课程库，按课程保存学习位置、练习、错题、复习和测评记录。
- AI 教学入口，根据课程状态组织讲解、前置补充、理解检查和后续学习。
- 试卷学习入口，围绕权威考试原题与用户资料提供学习、考试和复盘流程。
- 课程阶段测评从当前错题、到期复习和近期错误事实中优先选取客观题；数据不足时明确退回确定性课程题序，
  服务端保存题目快照、判分并回写错题、复习计划和课程学习事件。
- 用户可将有限结构化 Markdown/文本、文本型 PDF 或 DOCX 预览后导入私有试卷；缺少答案时先保存草稿，由 AI 给出受校验建议，
  再经所有者逐题复核才启用；PDF/DOCX 原文件可由所有者下载并在发送前校验长度、类型和哈希，默认每位
  用户累计最多保存 100MB；导入入口可查看用量并进入仅本人可见的分页原文件清单，按关联草稿或试卷
  下载和执行受引用保护的删除。原始资料
  全程可追溯且默认仅本人可见；未确认草稿和没有学习事实引用的
  私有试卷可由所有者显式删除。
- 互动课件与题目讲解，在适合交互的知识和题目中辅助建立过程理解。
- AI 生成并经审查的针对性练习，用于教学检查、错题复盘和阶段测评。
- 管理端提供 AI 变式题审查队列；批准后发布为保留母题来源的正式题，待审或驳回内容不会进入测评。
- 独立的用户学习端与管理系统目标，共用 Spring Boot 后端、账号和业务数据。

现有题库、真实判分、错题、间隔重复、模拟考试、内容治理和 AI 运营能力继续维护并
逐步接入统一课程学习状态。尚未实现的目标能力不会在本文中冒充当前结果。

当前阶段和最新验证见[项目状态](docs/project/status.md)。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 后端 | Java 21、Spring Boot 3、MyBatis-Plus、Spring Security、JWT、Validation、Knife4j |
| 数据 | MySQL 8、Flyway |
| 部署 | Docker、Docker Compose、Nginx |
| 测试 | JUnit、Mockito、MockMvc、Testcontainers、Vitest、Playwright |

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
- [架构决策](docs/architecture/decisions/index.md)
- [API 参考](docs/reference/api/index.md)
- [数据库参考](docs/reference/database/index.md)
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

精确契约以后端代码、迁移和[API 参考](docs/reference/api/index.md)为准。

## 许可证

本项目仅供个人学习和简历展示使用。
