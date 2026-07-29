# 测试策略

本文档定义项目自动化测试的分层、保留原则和新增门槛。目标不是追求测试数量，而是用可维护的测试保护高风险业务。

## 1. 当前测试层级

### 后端

- 领域与服务单元测试：判分、考试、刷题、错题、限流等核心规则。
- Controller MockMvc 测试：权限、参数校验、统一响应和异常映射。
- 安全测试：JWT 生成、解析和认证过滤器。
- AI Provider 测试：上游响应解析和异常处理。

### 前端

- 工具与 Store 测试：Token、登录状态和本地持久化。
- 路由守卫测试：认证、角色权限和重定向。
- 组件与页面测试：安全渲染、表单提交和关键交互。
- API 封装测试：请求路径、HTTP 方法、参数结构和特殊响应处理。
- Playwright 浏览器 E2E：在隔离 Docker 环境中覆盖真实验证码登录、JWT 会话和关键页面跳转。
- ESLint：检查 Vue/TypeScript 的未使用变量、无效表达式和 Vue 模板基础规则；存量显式 `any` 当前以警告呈现，新代码应优先使用明确类型或 `unknown`。
- Prettier：通过 `npm run format:check` 检查工程配置与本轮完成结构拆分的题目管理模块；存量页面按后续修改范围逐步纳入，避免一次性格式化制造大范围无语义 diff。

前端 DOM 环境统一使用 `happy-dom`，不同时维护多个 DOM 模拟实现。

## 2. 必须新增测试的情况

- 修改判分、考试提交、错题归集、权限或限流规则。
- 修复线上或验收中发现的缺陷，需要回归用例防止复发。
- 新增参数校验、异常码、事务或并发控制。
- 新增复杂前端状态、路由权限、流式响应解析或安全过滤逻辑。
- 修改前后端公共契约，且错误会影响多个页面或业务流程。

## 3. 通常不新增测试的情况

- 只改变简单 API 封装中的 ID、分页值或筛选值。
- 同一函数已覆盖请求路径、方法和参数传递，仅增加另一组等价参数。
- 纯样式、文案或无行为变化的布局调整。
- 框架和第三方库自身已经保证的行为。
- 仅为了提高测试数量或覆盖率百分比。

简单 CRUD API 每个导出函数通常保留一个代表性契约用例即可。错误分支只有在本项目包含额外处理逻辑时才单独测试。

## 4. 后续优先级

1. 使用真实 MySQL 和 Flyway 验证关键数据库迁移与业务约束。
2. 为新增的高风险业务流程补充端到端覆盖；当前基线已覆盖登录、刷题错题复习、考试及投稿审核入库。
3. 按缺陷和业务风险补测试，不再按文件或接口数量追求全覆盖。

## 5. 浏览器 E2E 环境

浏览器测试使用独立的 Spring `e2e` Profile（`docker-compose.e2e.yml`），仅在该 Profile 下将验证码答案固定为 `42`；它不会绕过账号密码、JWT、权限或路由守卫。开发和生产 Profile 仍使用随机一次性数学验证码。

本地先启动隔离环境，再执行浏览器 E2E：

```bash
# 若此前运行过普通开发 Profile，必须强制重建，使 backend 切换到 e2e Profile。
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --force-recreate --wait
cd frontend
npm run test:e2e
```

结束后执行：

```bash
docker compose -f docker-compose.yml -f docker-compose.e2e.yml down -v
```

## 6. Agent 临时浏览器流程验收

当用户要求“模拟用户试试”“打开浏览器跑一下流程”“看看这个页面能不能用”等临时验收时，先阅读 `skills/frontend-flow-test/SKILL.md`。该 skill 用于约束 Agent 选择最小业务闭环，并用低 token 方式记录关键状态。

临时浏览器验收不替代正式自动化测试：

- 若只是验证页面是否能进入、按钮是否能点、结果弹窗是否出现，执行与当前任务相关的最小闭环即可。
- 若发现真实缺陷，按风险决定是否补 Vitest、后端测试或 Playwright E2E 回归用例。
- 若准备发布、演示或修改了鉴权、路由、请求封装、全局布局等共享能力，应优先运行既有自动化测试，再补少量浏览器冒烟检查。
- 默认不要输出完整 DOM 或每步截图；失败时再逐级增加局部 DOM、截图、日志和后端容器日志。

## 7. 运行命令

```bash
cd frontend
npm test
npm run build
```

```bash
cd backend
mvn test
```

默认后端单测会排除 `@Tag("integration")` 的 Testcontainers 集成测试。如需单独运行集成测试，清空默认排除项并按需指定类名：

```bash
cd backend
mvn test -DexcludedGroups= -Dgroups=integration
mvn test -DexcludedGroups= -Dtest=WrongQuestionServiceIntegrationTest
mvn test -DexcludedGroups= -Dtest=AiVariantTrainingIntegrationTest
```

当前 Testcontainers 版本为 `1.21.4`，用于兼容 Docker Desktop / Docker Engine 29 的 API 变化。集成测试容器数据库名与基线迁移保持为 `learn_platform`，Flyway 使用容器 root 用户执行迁移，业务数据源仍使用测试用户。现有 5 个集成测试类共 55 个用例；其中 `AiVariantTrainingIntegrationTest` 专门验证 Flyway V18/V19、变式训练唯一约束、幂等开始、旧版重复完成、结构化首次判分和正确率聚合。

GitHub Actions 将 Testcontainers 集成测试作为独立 job 执行，避免默认 `excludedGroups=integration` 让真实 MySQL 约束退出提交门禁。后端常规 job 使用 `mvn clean verify`，同时执行 Checkstyle、SpotBugs 和 JaCoCo 报告生成。

若本地 Docker CLI 可用但 Testcontainers 报无法找到 Docker environment，先确认依赖版本、Docker context 和 socket 路径；不要把 0 tests 当作集成测试通过。
