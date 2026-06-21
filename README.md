# AI 题库与错题复习系统

[![CI](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml/badge.svg)](https://github.com/umico8832/LearnPlatform/actions/workflows/ci.yml)

一款面向学习者和备考人群的在线刷题平台，结合 AI 能力提供智能解析、错题复习、模拟考试和个性化学习建议。

## 功能特性

### 核心功能
- **题库管理**：支持单选、多选、判断、填空、简答等多种题型
- **在线刷题**：按课程、知识点、随机、顺序等多种练习模式
- **自动判分**：提交答案即时反馈，展示正确答案和解析
- **错题本**：答错自动收集，支持错题重练和掌握状态管理
- **模拟考试**：手动/随机组卷，倒计时考试，自动判分和成绩分析
- **AI 辅助**：AI 流式生成题目解析和变式题，并提供复习建议与知识点总结
- **数据统计**：学习趋势、正确率、知识点掌握雷达图等可视化面板
- **智能复习与搜索**：SM-2 间隔重复复习、全局搜索、搜索历史与热门搜索
- **学习与内容闭环**：学习诊断、题目投稿审核、AI 质检、题目来源追踪与复审

### 后台管理
- 课程和知识点管理
- 题目管理（CRUD、启用/禁用）
- 试卷管理（创建、组卷、发布）
- 平台数据总览

## 技术栈

### 前端
| 技术 | 说明 |
|------|------|
| Vue 3 | 前端框架，Composition API |
| TypeScript | 类型安全 |
| Vite | 构建工具 |
| Element Plus | UI 组件库 |
| Pinia | 状态管理 |
| Vue Router | 路由管理 |
| Axios | HTTP 请求 |
| ECharts | 图表可视化 |

### 后端
| 技术 | 说明 |
|------|------|
| Java 17+ | 编程语言（推荐 JDK 21+） |
| Spring Boot 3 | 应用框架 |
| MyBatis-Plus | ORM 框架 |
| Spring Security | 安全框架 |
| JWT | 鉴权方案 |
| Knife4j | 接口文档（Swagger 增强） |
| Validation | 参数校验 |

> **注意**：本项目已移除 Lombok（JDK 26 兼容性问题），所有 Java 实体类使用手写 getter/setter/toString。

### 数据库 & 部署
| 技术 | 说明 |
|------|------|
| MySQL 8.0 | 主数据库 |
| Flyway | 数据库版本迁移 |
| Docker | 容器化 |
| Docker Compose | 多容器编排 |
| Nginx | 反向代理 |

## 项目结构

```
LearnPlatform/
├── frontend/                    # 前端项目（Vue 3 + TypeScript）
│   ├── src/
│   │   ├── api/                 # API 请求模块
│   │   ├── components/          # 公共组件
│   │   ├── router/              # 路由配置
│   │   ├── stores/              # Pinia 状态管理
│   │   ├── types/               # TypeScript 类型
│   │   ├── utils/               # 工具函数
│   │   └── views/               # 页面视图
│   ├── package.json
│   └── vite.config.ts
├── backend/                     # 后端项目（Spring Boot 3）
│   ├── src/main/java/com/learnplatform/
│   │   ├── config/              # 配置类
│   │   ├── common/              # 公共模块（响应体、异常处理）
│   │   ├── entity/              # 实体类
│   │   ├── mapper/              # MyBatis Mapper
│   │   ├── service/             # Service 层
│   │   ├── controller/          # Controller 层
│   │   ├── dto/                 # 数据传输对象
│   │   ├── security/            # 安全模块（JWT）
│   │   └── service/ai/          # AI Provider 抽象层
│   ├── src/main/resources/
│   │   ├── application.yml      # 应用配置
│   │   └── db/migration/        # Flyway 数据库迁移脚本
│   └── pom.xml
├── docs/                        # 项目文档
│   ├── PRD.md                   # 产品需求文档
│   ├── ARCHITECTURE.md          # 架构设计
│   ├── DB_DESIGN.md             # 数据库设计
│   ├── API_DESIGN.md            # 接口设计
│   ├── ROADMAP.md               # 开发路线图
│   ├── RESUME.md                # 简历项目描述
│   ├── DEMO.md                  # 演示流程
│   └── CHANGELOG_AGENT.md       # 开发日志
├── docker-compose.yml           # Docker 编排
├── .env.example                 # 环境变量示例
└── README.md                    # 项目说明
```

## 快速开始

### 方式一：本地开发

#### 环境要求
- JDK 21+（推荐 JDK 26）
- Maven 3.8+
- Node.js 20+（Docker 构建使用 Node.js 22）
- MySQL 8.0+

#### 1. 克隆项目
```bash
git clone https://github.com/umico8832/LearnPlatform.git
cd LearnPlatform
```

#### 2. 初始化数据库
```bash
mysql -u root -p < backend/src/main/resources/db/migration/V1__baseline.sql
```

#### 3. 配置环境变量
```bash
cp .env.example .env
# 编辑 .env 文件，填写数据库密码、JWT 密钥等
```

本地通过 Maven 启动时，需要先在项目根目录加载 `.env`。请使用项目提供的加载脚本，不要直接 `source .env`（数据库 URL 中的 `&` 会被 shell 当作控制符）：

```bash
source scripts/load-env.sh .env
```

#### 4. 启动后端
```bash
cd backend
mvn spring-boot:run
```
后端启动后访问接口文档：http://localhost:8080/doc.html

#### 5. 启动前端
```bash
cd frontend
npm install
npm run dev
```
前端访问：http://localhost:5173

### 方式二：Docker Compose 一键启动

#### 环境要求
- Docker 20.10+
- Docker Compose 2.0+

#### 1. 配置环境变量
```bash
cp .env.example .env
# 编辑 .env 文件，填写配置
```

#### 2. 启动所有服务
```bash
docker compose up -d
```

如本机 80 或 8080 端口已被占用，可在 `.env` 中调整：
```env
FRONTEND_HOST_PORT=18000
BACKEND_HOST_PORT=18080
```

#### 3. 查看服务状态
```bash
docker compose ps
```

#### 4. 访问
- 前端页面：http://localhost
- 后端接口：http://localhost:8080
- 接口文档：http://localhost:8080/doc.html

首次创建数据库时会自动写入演示课程、知识点、题目和试卷，可直接使用下方账号体验完整流程。

#### 5. 停止服务
```bash
docker compose down
```

## 演示账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 普通用户 | testuser | test123 |

> 注意：以上为开发环境演示账号，生产环境请修改密码。

## 演示流程

详见 [docs/DEMO.md](docs/DEMO.md)，包含完整的功能演示步骤和截图说明。

## 接口文档

后端启动后，访问 Knife4j 接口文档：
- 本地开发：http://localhost:8080/doc.html
- Docker 部署：http://localhost:8080/doc.html

## 开发计划

详见 [docs/ROADMAP.md](docs/ROADMAP.md)

自动化测试的分层、范围和新增门槛见 [docs/TESTING.md](docs/TESTING.md)。

当前工作重点是完成考试端到端演示、真实截图和 CI 实跑验证；后续 AI 迭代将优先建设 token/成本、用户独立配额和调用报告等运营治理能力，详见 [docs/ROADMAP.md](docs/ROADMAP.md)。

| 阶段 | 名称 | 状态 |
|:----:|------|:----:|
| 0 | 项目规划 | ✅ |
| 1 | 项目骨架 | ✅ |
| 2 | 用户与鉴权 | ✅ |
| 3 | 课程与知识点 | ✅ |
| 4 | 题库系统 | ✅ |
| 5 | 刷题与判分 | ✅ |
| 6 | 错题本 | ✅ |
| 7 | 试卷与考试 | ✅ |
| 8 | AI 功能 | ✅ |
| 9 | 统计可视化 | ✅ |
| 10 | 质量提升 | ✅ |
| 11 | 部署与简历 | ✅ |
| 12 | 体验增强迭代 | ✅ 基本完成 |
| 13 | AI 题目学习资产 | ✅ |
| 14 | AI 可视化交互讲解 | ✅ |
| 15 | AI 学习画像与个性化推荐 | 🚧 |
| 16 | 题目投稿与 AI 题库生产 | 🚧 |
| 17 | 间隔重复与智能复习 | ✅ 基本完成 |
| 18 | 全局搜索与快捷导航 | ✅ 基本完成 |
| 19 | AI 调用分析与成本控制 | ✅ 基本完成 |
| 20 | 演示验收与 AI 运营治理 | 📋 规划中 |

## 项目规范

### 接口规范
- 统一响应结构：`{ "code": 0, "message": "success", "data": {} }`
- RESTful 风格
- 普通用户接口：`/api/**`
- 管理端接口：`/api/admin/**`

### 数据库规范
- 表名和字段名：snake_case
- 业务主表通常包含：create_time、update_time、deleted，并使用逻辑删除
- 只追加的审计记录表可不包含 update_time / deleted，具体以 `docs/DB_DESIGN.md` 和 Flyway 迁移为准

### Git 提交规范

Git、commit、分支、回滚和历史操作规则见 [docs/AGENT_GIT_RULES.md](docs/AGENT_GIT_RULES.md)。

## 常见问题

### Q: 后端启动失败，提示数据库连接错误？
A: 请确认 MySQL 服务已启动，并检查 `.env` 文件中的数据库配置是否正确。

### Q: 前端请求后端接口报 404？
A: 本地开发时确认后端已启动在 8080 端口，且 Vite 代理配置正确。

### Q: AI 功能不可用？
A: AI 功能需要配置有效的 API Key。在 `.env` 文件中设置 `AI_API_KEY`，并将 `AI_ENABLED` 设为 `true`。

### Q: Docker 启动后前端无法访问后端？
A: 确认 docker compose 中所有服务已正常启动（`docker compose ps`），检查 Nginx 配置中的反向代理地址。

### Q: 后端编译报 Lombok 相关错误？
A: 本项目已移除 Lombok（JDK 26 兼容性问题），所有 Java 实体类使用手写 getter/setter/toString。请确保使用 JDK 21+ 编译。

### Q: 如何查看 API 接口文档？
A: 后端启动后访问 http://localhost:8080/doc.html 即可查看 Knife4j 接口文档，包含所有接口的请求参数和响应格式。

### Q: 如何初始化测试数据？
A: 数据库 schema.sql 包含建表 SQL。测试账号：管理员 admin/admin123，普通用户 testuser/test123。

## 文档索引

| 文档 | 说明 |
|------|------|
| [PRD.md](docs/PRD.md) | 产品需求文档 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构设计文档 |
| [DB_DESIGN.md](docs/DB_DESIGN.md) | 数据库设计文档 |
| [API_DESIGN.md](docs/API_DESIGN.md) | 接口设计文档 |
| [ROADMAP.md](docs/ROADMAP.md) | 开发路线图 |
| [DEMO.md](docs/DEMO.md) | 演示流程文档 |
| [RESUME.md](docs/RESUME.md) | 简历项目描述和面试问答 |
| [CHANGELOG_AGENT.md](docs/CHANGELOG_AGENT.md) | 开发日志 |
| [HANDOFF.md](docs/HANDOFF.md) | Agent 交接文档 |
| [AGENT_GIT_RULES.md](docs/AGENT_GIT_RULES.md) | Agent Git、commit、分支和回滚规则 |
| [AGENT_REVIEW_CHECKLIST.md](docs/AGENT_REVIEW_CHECKLIST.md) | 通用 Agent 审查清单 |
| [ENGINEERING_RULES.md](docs/ENGINEERING_RULES.md) | 工程判断和代码归属建议清单 |

## 许可证

本项目仅供学习和简历展示使用。
