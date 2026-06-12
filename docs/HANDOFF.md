# AI 题库与错题复习系统 - Agent 交接文档

本文档用于跨对话、跨 Agent、跨开发阶段交接项目状态。
新 Agent 接手时，必须先阅读本文件，再结合 `AGENTS.md`、`README.md`、`docs/ROADMAP.md`、`docs/CHANGELOG_AGENT.md` 和实际代码判断当前状态。

---

## 1. 项目基本信息

项目名称：AI 题库与错题复习系统
项目定位：用于学习、刷题、错题复习和 AI 辅助学习的中大型 Web 项目
开发环境：macOS (本地 MySQL 8.0.43、JDK 26、Maven 3.9.16、Node v22)
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

---

## 2. 当前项目阶段

当前阶段：Phase 2 — 用户与鉴权（待开始）

阶段状态：
- [x] Phase 0：项目规划 ✅
- [x] Phase 1：项目骨架 ✅（已验证可运行）
- [ ] Phase 2：用户与鉴权（待开始）
- [ ] Phase 3 ~ Phase 11：待开始

---

## 3. 已完成内容

### 已完成模块
- 项目文档体系（PRD、ARCHITECTURE、DB_DESIGN、API_DESIGN、ROADMAP、RESUME、CHANGELOG_AGENT）
- .gitignore、.env.example、README.md、AGENTS.md

### 已完成后端代码
- pom.xml（Spring Boot 3.2.5 + MyBatis-Plus + JWT + Knife4j + Security，**已移除 Lombok**）
- LearnPlatformApplication.java 启动类
- application.yml 主配置（数据库、JWT、AI、Knife4j 环境变量注入）
- R.java 统一响应体 + ResultCode 枚举（手写 getter/setter，无 Lombok）
- BusinessException + GlobalExceptionHandler 全局异常处理（手写 getCode()，SLF4J Logger）
- MyBatisPlusConfig（分页插件 + 自动填充）
- CorsConfig、Knife4jConfig、SecurityConfig（Phase 1 暂时放行）
- PublicController（健康检查 GET /api/public/health）
- schema.sql（13 张表 + 初始测试数据，已导入 MySQL）
- Dockerfile

### 已完成前端代码
- package.json（Vue 3 + Element Plus + Pinia + Axios + ECharts）
- npm install 完成（159 packages）
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

- Phase 2：用户与鉴权（JWT 实现）
- Phase 3 ~ Phase 11

---

## 5. 运行方式

### 本地开发（已验证通过）

#### 启动 MySQL
```bash
sudo /usr/local/mysql/support-files/mysql.server start
```

#### 启动后端
```bash
cd backend
mvn spring-boot:run
# 访问：http://localhost:8080
# 健康检查：http://localhost:8080/api/public/health
# Knife4j 文档：http://localhost:8080/doc.html
```

#### 启动前端
```bash
cd frontend
npm run dev
# 访问：http://localhost:5173
```

### Docker（一键启动）
```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

---

## 6. 当前遗留问题

- README.md 中的 JDK 版本说明需更新（实际使用 JDK 26，需 Java 17+ 编译目标）
- Lombok 已移除，后续新增实体类需手写 getter/setter/toString
- schema.sql 中的 BCrypt 密码哈希值需要在 Phase 2 验证是否正确

---

## 7. 下一步建议任务

任务名称：Phase 2 - 用户与鉴权

任务目标：
- 后端：User 实体、UserMapper、JwtTokenProvider、JwtAuthenticationFilter、AuthService、AuthController
- 后端：更新 SecurityConfig 权限规则（公开接口 vs 受保护接口）
- 前端：完善 API 封装、user store、登录/注册页面接真实接口
- 前端：路由守卫（未登录跳转登录页）

验收标准：
1. 用户可以通过 POST /api/auth/register 注册
2. 用户可以通过 POST /api/auth/login 登录并获得 JWT
3. 携带 JWT 可以访问受保护接口
4. 未携带 JWT 访问受保护接口返回 401
5. 前端可以正常注册、登录
6. 登录状态刷新后保持
7. 退出登录清除 Token

---

## 8. 新对话续接提示词

```
你现在接手一个长期开发中的全栈 Web 项目。

项目名称：AI 题库与错题复习系统
开发环境：macOS（本地 MySQL 8.0、JDK 26、Maven 3.9.16、Node v22）
技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia | Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + JWT + Knife4j | Docker Compose

重要注意：本项目已移除 Lombok（JDK 26 兼容性问题），所有 Java 实体类需要手写 getter/setter/toString。

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

当前阶段：Phase 1 已完成并验证通过，下一步进入 Phase 2 用户与鉴权。

本地运行方式：
- MySQL: sudo /usr/local/mysql/support-files/mysql.server start
- 后端: cd backend && mvn spring-boot:run
- 前端: cd frontend && npm run dev
```

---

## 9. 交接注意事项

- 不要依赖旧对话记忆
- 不要把 AGENTS.md 当进度表
- 不要清空 docs/CHANGELOG_AGENT.md
- 不要覆盖真实 .env
- 不要提交真实 API Key
- 先检查代码，再相信文档
- 发现文档与代码不一致时，以代码为准，并修正文档
- **不要使用 Lombok**，手写 getter/setter/toString（JDK 26 兼容性）