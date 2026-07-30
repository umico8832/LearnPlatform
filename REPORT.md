# LearnPlatform 工程体检报告

体检日期：2026-06-25

## 当前项目技术栈

- 前端：Vue 3、TypeScript、Vite 8、Element Plus、Pinia、Vue Router、Axios、ECharts、Vitest、Playwright。
- 后端：Java 17+、Spring Boot 3.2.5、Spring Security、MyBatis-Plus、JWT、Knife4j、Validation、Flyway、Redis、Maven。
- 数据与部署：MySQL 8、Docker、Docker Compose、Nginx、Prometheus/Grafana/Loki 监控配置。

## 已执行检查

| 检查项 | 命令 | 结果 |
| --- | --- | --- |
| 前端依赖安装 | `cd frontend && npm ci` | 通过；初始发现 2 个 npm audit 漏洞 |
| 前端安全审计 | `cd frontend && npm audit --audit-level=moderate` | 修复后通过，0 vulnerabilities |
| 前端单元测试 | `cd frontend && npm test -- --run` | 通过，24 个文件、196 个测试 |
| 前端生产构建 | `cd frontend && npm run build` | 通过 |
| 后端单元测试 | `cd backend && mvn test` | 通过，360 个测试 |
| 后端打包 | `cd backend && mvn package -DskipTests` | 通过 |
| Docker Compose 配置解析 | `docker compose config --quiet` | 通过 |

## 已发现的问题

1. 前端 lockfile 中存在 2 个 npm audit 漏洞：
   - `dompurify <= 3.4.10`：moderate，涉及 DOMPurify 配置污染相关安全公告。
   - `form-data 4.0.0 - 4.0.5`：high，间接依赖，涉及 multipart 字段名/文件名 CRLF 注入。
2. 项目当前没有配置前端 lint 脚本或 ESLint 配置，无法执行 `npm run lint`。
3. 前端构建存在非阻断警告：
   - `@vueuse/core` 的 `/* #__PURE__ */` 注释位置被 Rolldown 忽略。
   - 部分产物 chunk 超过 500 kB。
4. `npm ci` 在本机提示 3 个依赖含未审批 install scripts：`fsevents` 两个版本和 `vue-demi`。本轮未自动批准脚本，避免改变本机 npm 安全策略。
5. 后端测试在本机 JDK 上存在非阻断运行时警告：ByteBuddy 动态 agent、Unsafe 废弃 API、测试中反射修改 final 字段等；测试结果通过。

## 已修复的问题

1. 执行 `npm audit fix`，将 `frontend/package-lock.json` 中的：
   - `dompurify` 从 `3.4.10` 更新到 `3.4.11`；
   - `form-data` 从 `4.0.5` 更新到 `4.0.6`。
2. 修复后 `npm audit --audit-level=moderate` 返回 `found 0 vulnerabilities`。
3. 复跑前端测试、前端构建、后端测试、后端打包和 Docker Compose 配置解析，均通过。

## 仍存在的问题

1. 前端 lint 尚未配置。建议后续单独引入 ESLint + Vue/TypeScript 规则，并先以 warning 或有限目录方式接入，避免一次性产生大量格式化噪音。
2. 前端构建的大 chunk 警告仍存在。主要来自 Element Plus、ECharts、Mermaid/可视化相关依赖，建议后续按页面和图表能力继续做动态加载与 chunk 拆分。
3. `@vueuse/core` pure annotation 警告来自第三方包和 Vite/Rolldown 解析行为，本轮未改动第三方构建链。
4. 本轮未运行 Playwright E2E。完整 E2E 需要启动隔离 Docker 环境，命令见 `docs/development/testing.md`。
5. 本轮未运行 Testcontainers 集成测试。默认 `mvn test` 已排除 `integration` 分组，如需验证真实 MySQL 约束，可执行 `cd backend && mvn test -Dgroups=integration`。

## 建议的下一步开发方向

1. 继续 Phase 21 P2/P3：整理 Practice、WrongQuestion、Review、ExamList 以及管理端列表体验。
2. 为前端补充正式 lint 脚本和 CI 步骤，统一 TypeScript/Vue 代码质量基线。
3. 对 Mermaid、ECharts、Element Plus 等大依赖做更细粒度动态加载，降低首屏 chunk。
4. 推送后观察 GitHub Actions 的 Backend、Frontend、Docker、Browser E2E 四个 Job 实跑结果。
5. 在 Docker E2E 环境中补一次完整浏览器验收，并产出演示截图。

## 如何本地运行项目

### 本地开发

```bash
# 启动 MySQL
sudo /usr/local/mysql/support-files/mysql.server start

# 加载环境变量
source scripts/load-env.sh .env

# 启动后端
cd backend
mvn spring-boot:run

# 启动前端
cd frontend
npm ci
npm run dev
```

访问地址：

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- Knife4j：http://localhost:8080/doc.html

### Docker Compose

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

默认访问：

- 前端：http://localhost
- 后端：http://localhost:8080

## 如何运行测试和构建

```bash
# 前端依赖、安全审计、测试、构建
cd frontend
npm ci
npm audit --audit-level=moderate
npm test -- --run
npm run build
```

```bash
# 后端测试和打包
cd backend
mvn test
mvn package -DskipTests
```

```bash
# Docker Compose 配置检查
docker compose config --quiet
```

```bash
# 浏览器 E2E（会启动隔离 Docker 环境）
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --force-recreate --wait
cd frontend
E2E_BASE_URL=http://localhost:18000 npm run test:e2e
```
