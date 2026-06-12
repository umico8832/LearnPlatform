# AI 题库与错题复习系统 - Agent 交接文档

本文档用于跨对话、跨 Agent、跨开发阶段交接项目状态。
新 Agent 接手时，必须先阅读本文件，再结合 `AGENTS.md`、`README.md`、`docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 和实际代码判断当前状态。

---

## 1. 项目基本信息

项目名称：AI 题库与错题复习系统
项目定位：用于学习、刷题、错题复习和 AI 辅助学习的中大型 Web 项目
开发环境：WSL2 + Ubuntu
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

---

## 2. 当前项目阶段

当前阶段：Phase 1 — 项目骨架（代码已完成，待验证运行）

阶段状态：
- [x] Phase 0：项目规划 ✅
- [ ] Phase 1：项目骨架（代码已创建，需安装开发环境后验证）
- [ ] Phase 2：用户与鉴权
- [ ] Phase 3 ~ Phase 11：待开始

---

## 3. 已完成内容

### 已完成模块
- 项目文档体系（PRD、ARCHITECTURE、DB_DESIGN、API_DESIGN、ROADMAP、RESUME、CHANGELOG_AGENT）
- .gitignore、.env.example、README.md、AGENTS.md

### 已完成后端代码
- pom.xml（Spring Boot 3.2.5 + MyBatis-Plus + JWT + Knife4j + Security）
- LearnPlatformApplication.java 启动类
- application.yml 主配置（数据库、JWT、AI、Knife4j 环境变量注入）
- R.java 统一响应体 + ResultCode 枚举
- BusinessException + GlobalExceptionHandler 全局异常处理
- MyBatisPlusConfig（分页插件 + 自动填充）
- CorsConfig、Knife4jConfig、SecurityConfig（Phase 1 暂时放行）
- PublicController（健康检查 GET /api/public/health）
- schema.sql（13 张表 + 初始测试数据）
- Dockerfile

### 已完成前端代码
- package.json（Vue 3 + Element Plus + Pinia + Axios + ECharts）
- vite.config.ts（代理 /api → localhost:8080、Element Plus 自动导入、路径别名）
- main.ts + App.vue + global.css
- types/api.ts、types/user.ts
- utils/auth.ts（Token 管理）、utils/request.ts（Axios 封装）
- router/index.ts（路由守卫）
- stores/user.ts（Pinia 用户 Store）
- components/layout/AppLayout.vue（侧边栏 + 顶部导航布局）
- views/home/HomeView.vue（调用健康检查接口）
- views/auth/LoginView.vue、RegisterView.vue
- views/NotFoundView.vue
- Dockerfile + nginx.conf

### 已完成部署配置
- docker-compose.yml（MySQL + Backend + Frontend 三服务）

---

## 4. 未完成内容

- 开发环境安装（JDK 21、Maven、Node.js 18）— 需要 sudo 权限
- `npm install` 安装前端依赖
- `mvn spring-boot:run` 或 `docker-compose up` 验证项目可运行
- Phase 2：用户与鉴权（JWT 实现）
- Phase 3 ~ Phase 11

---

## 5. 当前目录结构

```text
LearnPlatform/
├── AGENTS.md
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── DB_DESIGN.md
│   ├── API_DESIGN.md
│   ├── ROADMAP.md
│   ├── CHANGELOG_AGENT.md
│   ├── HANDOFF.md
│   └── RESUME.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/learnplatform/
│       │   ├── LearnPlatformApplication.java
│       │   ├── config/（CorsConfig、Knife4jConfig、MyBatisPlusConfig、SecurityConfig）
│       │   ├── common/result/（R.java、ResultCode.java）
│       │   ├── common/exception/（BusinessException、GlobalExceptionHandler）
│       │   └── controller/（PublicController）
│       └── resources/
│           ├── application.yml
│           └── db/schema.sql
├── frontend/
│   ├── package.json
│   ├── tsconfig.json、tsconfig.node.json
│   ├── vite.config.ts
│   ├── index.html
│   ├── env.d.ts
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── main.ts、App.vue
│       ├── assets/styles/global.css
│       ├── components/layout/AppLayout.vue
│       ├── router/index.ts
│       ├── stores/user.ts
│       ├── types/（api.ts、user.ts）
│       ├── utils/（auth.ts、request.ts）
│       └── views/（home/HomeView、auth/LoginView、auth/RegisterView、NotFoundView）
└── skills/
```

---

## 6. 运行方式

### 前端
```bash
cd frontend
npm install
npm run dev
```

### 后端
```bash
cd backend
mvn spring-boot:run
```

### Docker（一键启动）
```bash
docker compose up -d
docker compose ps
```

---

## 7. 当前遗留问题

- 开发环境（JDK 21、Maven、Node.js 18）需要用户自行安装（sudo 权限）
- SecurityConfig 暂时放行所有请求，Phase 2 需接入 JWT 鉴权
- schema.sql 中的 BCrypt 密码哈希值需要在 Phase 2 验证是否正确
- 前端 TS 报错全部是因为依赖未安装（npm install 后自动解决）

---

## 8. 下一步建议任务

任务名称：安装开发环境并验证项目可运行

任务目标：
- 安装 JDK 21、Maven、Node.js 18
- 运行 npm install 安装前端依赖
- 运行 docker compose up 或分别启动前后端，验证项目可运行
- 验证健康检查接口和前端页面

验收标准：
- 后端启动成功，GET /api/public/health 返回正常
- 前端启动成功，http://localhost:5173 可访问
- 前端能通过 Vite 代理请求后端接口

---

## 9. 新对话续接提示词

```
你现在接手一个长期开发中的全栈 Web 项目。

项目名称：AI 题库与错题复习系统
开发环境：WSL2 + Ubuntu
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

请先阅读以下文件：
1. AGENTS.md
2. README.md
3. docs/ROADMAP.md
4. docs/CHANGELOG_AGENT.md
5. docs/HANDOFF.md

工作方式：
1. 先根据 docs/HANDOFF.md 理解当前项目状态；
2. 再根据 docs/ROADMAP.md 判断当前阶段；
3. 再根据代码实际情况验证文档是否过时；
4. 自动选择下一步最高优先级任务；
5. 继续开发、测试、修复、更新文档；
6. 除非遇到重大方向问题，否则不要频繁问我；
7. 每轮结束都要更新 docs/CHANGELOG_AGENT.md 和必要文档。

当前阶段：Phase 1 项目骨架代码已创建，需要安装开发环境并验证运行，然后进入 Phase 2。
```

---

## 10. 交接注意事项

- 不要依赖旧对话记忆
- 不要把 AGENTS.md 当进度表
- 不要清空 docs/CHANGELOG_AGENT.md
- 不要覆盖真实 .env
- 不要提交真实 API Key
- 先检查代码，再相信文档
- 发现文档与代码不一致时，以代码为准，并修正文档