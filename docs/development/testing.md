# 测试策略

本文档定义项目自动化测试的分层、保留原则和新增门槛。目标不是追求测试数量，而是用可维护的测试保护高风险业务。

涉及行为变化的开发流程同时遵守 `docs/development/workflow.md`。本项目对高风险行为和缺陷修复要求测试先行，但不把纯样式、文档和无行为机械修改包装成严格 TDD。

## 1. 测试层级

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
- ESLint：检查 Vue/TypeScript 的未使用变量、无效表达式和 Vue 模板基础规则。
- Prettier：通过 `npm run format:check` 检查已纳入格式化范围的文件。

前端 DOM 环境统一使用 `happy-dom`，不同时维护多个 DOM 模拟实现。

### 仓库工具

- `scripts/tests/` 使用 Python 标准库 `unittest` 验证文档、Git 和其他仓库级检查工具的核心规则。
- 工具测试必须覆盖正常输入、边界和主要拒绝路径，不能只在 CI 中用一个真实样例冒烟。
- 仓库工具保持无第三方运行时依赖，避免为了轻量检查额外建立 Python 依赖环境。

## 2. 分层验证：L1 / L2 / L3

按改动风险选择验证范围，不机械地在每个小改动后运行全量测试。三个层级是执行规则，
不是测试分类法；同一个测试可以服务于不同层级。

### L1：开发循环验证

适用于 Red / Green、局部功能开发、单个 Service、Controller、Vue 组件和明确缺陷修复。

默认只运行：

- 新增或修改的测试；
- 直接受影响的相关测试；
- 必要的单个 Integration 测试（例如改动涉及真实数据库约束时）。

目标是快速反馈，通常控制在几十秒到几分钟。禁止每一个小修改都自动运行 backend 全量、
frontend 全量、全部 Testcontainers、全部 Playwright 或 Docker 全环境，除非局部测试
无法覆盖本轮高风险行为。Docker build、Compose 重建和完整 Docker E2E 都不是默认 L1；
普通局部修改不得为了验证而完整重建 Docker 环境。

临时浏览器检查复用[工作流中已核对的开发服务](workflow.md#运行环境与服务复用)，按页面影响选择
学习端或管理端地址；不因基础服务在 Docker 中就自动运行 `app-up` 或正式 E2E。

### L2：模块 / 业务闭环验证

适用于达到预先定义的 commit boundary、完成一个完整模块或完整业务闭环、准备创建
模块级 commit 时。按实际影响选择验证范围：

- 后端：受影响模块测试、必要静态检查和必要 build。
- 前端：受影响 Vitest、vue-tsc、ESLint 和必要 build。
- 数据库：相关的 Testcontainers。
- Docker：涉及 Dockerfile、`.dockerignore`、Compose 或 Nginx 时，按影响验证配置解析、构建或
  容器行为；仅运行时配置变化不必重新编译镜像。需要将当前源码更新到完整 Docker 环境时使用
  `app-up`。普通业务代码的模块验收优先使用本地服务，不自动触发 Docker 重建。
- 跨层关键流程：对应的最小 Playwright / 真实浏览器闭环。

不是所有 L2 都运行所有测试。例如后端普通查询筛选逻辑变化，不应自动运行 frontend
全测试、全部 Playwright 和所有 Integration，除非存在实际跨层风险。

### L3：项目级门禁

主要适用于 Phase Exit、Release、演示准备、大规模共享基础设施修改、鉴权 / 权限核心
变化、数据库核心基础设施变化、请求层 / 全局 Router 等共享能力变化、大规模重构，或
用户明确要求完整验证。

L3 根据风险可包括：backend clean verify、frontend 全测试 / coverage、lint、
typecheck、build、Integration、Playwright、docs checks 和 Docker build。

L3 仍然是按风险选择，不是为了展示测试数量机械执行所有命令。

## 3. 昂贵验证的重复执行规则

如果当前工作区状态已经通过某项昂贵验证，之后只修改了与其无关的内容，不要再次运行。

- 完整 Playwright 已通过，之后只修改文档：不跑 Playwright。
- backend clean verify 已通过，之后只修改前端 CSS：不跑 backend。
- 完整 frontend 已通过，之后只修改后端一个独立单测：不重新跑全部 frontend。

不要形成“修改 → 全量测试 → 修改 → 全量测试”的循环。开发阶段大量使用 L1，模块边界
使用 L2，Phase Exit 使用一次相称的 L3，最终完整门禁优先交给 CI。

## 5. 必须新增测试的情况

- 修改判分、考试提交、错题归集、权限或限流规则。
- 修复线上或验收中发现的缺陷，需要回归用例防止复发。
- 新增参数校验、异常码、事务或并发控制。
- 新增复杂前端状态、路由权限、流式响应解析或安全过滤逻辑。
- 修改公共接口契约、数据库约束或迁移。
- 修改 AI 结构化输出解析、私有答案隔离或真实完成事件。

以上场景默认先编写能够复现目标行为的失败测试，确认失败原因正确后再实现。若受外部环境、纯配置兼容或其他客观条件限制而无法形成有效 Red，必须在开发日志和交付总结中记录原因与替代验证。

## 6. 通常不新增测试的情况

- 只改变简单 API 封装中的 ID、分页值或筛选值。
- 同一函数已覆盖请求路径、方法和参数传递，仅增加另一组等价参数。
- 纯样式、文案或无行为变化的布局调整。
- 只更新文档，或无逻辑变化的机械重命名与格式化。
- 第三方工具生成且已有上层验证的代码。
- 框架和第三方库自身已经保证的行为。
- 仅为了提高测试数量或覆盖率百分比。

简单 CRUD API 每个导出函数通常保留一个代表性契约用例即可。错误分支只有在本项目包含额外处理逻辑时才单独测试。
不新增测试不等于不验证；根据改动选择文档检查、静态检查、构建或浏览器验证。

## 7. 测试投资优先级

1. 使用真实 MySQL 和 Flyway 验证关键数据库迁移与业务约束。
2. 为新增的高风险跨层业务流程补充端到端覆盖。
3. 按缺陷和业务风险补测试，不再按文件或接口数量追求全覆盖。

## 8. 浏览器 E2E 环境

浏览器测试使用独立的 Spring `e2e` Profile（`docker-compose.e2e.yml`），前端站点密钥与后端
校验密钥使用 Cloudflare Turnstile 测试配置；账号密码、JWT、权限和路由守卫仍执行真实逻辑。
测试密钥只用于隔离 E2E，不写入日常开发或生产配置；这些环境按自己的 Turnstile 配置完成验证。
E2E Compose 还提供 OpenAI 兼容的确定性上游响应，只替代不可重复且需要密钥的外部模型调用；后端的配额、
Prompt 构造、HTTP Provider、结构校验、调用审计、草稿状态和数据库事务仍使用真实实现。该响应不得在开发或
生产 Compose 中启用，也不能替代 AI 解析与异常分支的单元测试。

`docker-compose.e2e.yml` 使用独立 Compose 项目名 `learnplatform-e2e`，E2E 拥有自己的
容器、网络和数据卷，与开发环境隔离。`down -v` 只清理 E2E 数据，不会误删开发数据。

本地通过单一入口执行浏览器 E2E：

```bash
cd frontend
npm run test:e2e
```

聚焦运行现有用例时把 Playwright 参数传给同一入口：

```bash
npm run test:e2e -- --grep '用户可完成考试'
```

该命令由 `scripts/docker-lifecycle.py` 使用当前前后端源码构建隔离镜像、强制重建并等待
服务健康，然后运行 Playwright。成功、测试失败或启动异常都会进入 `finally`，按明确的
`learnplatform-e2e` 范围删除 E2E 容器、数据卷和两份应用镜像，再按
[Docker 磁盘增长治理](docker-disk-governance.md)回收悬空镜像与超预算缓存。若默认 18000
端口已被日常环境占用，脚本为本轮选择空闲端口并同步设置 Playwright Base URL。

`npm run test:e2e:playwright` 只是生命周期脚本内部使用的 Playwright 命令；Agent 不得单独
执行它并把已有容器的结果记作当前源码 E2E。

## 9. Agent 临时浏览器流程验收

临时浏览器检查的流程、取证与失败排查由项目
[frontend-flow-test](../../.agents/skills/frontend-flow-test/SKILL.md)维护。它不替代自动化回归；
发现缺陷后按本页的风险标准补充测试，发布、演示或共享能力变化优先复用现有自动化验证。

## 10. 运行命令

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
python3 scripts/check-code-structure.py
python3 scripts/check-docs.py
```

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

GitHub Actions 将 Testcontainers 集成测试作为独立 job 执行，避免默认 `excludedGroups=integration` 让真实 MySQL 约束退出提交门禁。后端常规 job 使用 `mvn clean verify`，同时执行 Checkstyle、SpotBugs 和 JaCoCo 报告生成。

覆盖率门槛是防倒退基线，不是追求数字的目标：

- 后端 JaCoCo：行覆盖率不低于 50%，分支覆盖率不低于 35%；
- 前端 Vitest 显式统计全部 `src/**/*.ts` 与 `src/**/*.vue`（排除生成的 `.d.ts`）：语句和行覆盖率不低于 12%，函数覆盖率不低于 10%，分支覆盖率不低于 8%。

新增高风险逻辑即使整体覆盖率仍高于门槛，也必须按业务风险补充针对性测试。提高门槛前应先确认本地和 CI 基线稳定，禁止通过排除未测试业务代码换取数字。

若本地 Docker CLI 可用但 Testcontainers 报无法找到 Docker environment，先确认依赖版本、Docker context 和 socket 路径；不要把 0 tests 当作集成测试通过。

## 仓库结构门禁

- `python3 scripts/check-repository-layout.py` 检查跨平台路径及生成物：不提交 `target`、`dist`、
  `node_modules`、报告、虚拟环境、缓存或系统垃圾；文件名不使用 `< > : " | ? *`，不以空格或句点结尾。
- `python3 scripts/check-code-structure.py` 检查 Service 行数与依赖数、Vue 文件及脚本块大小，
  阈值以脚本为准，用于触发职责审查，不代表低于阈值就合理。
- 管理页面只能放入 `frontend/src/admin/views/`；不得用异常扩展名或路径保存不参与构建、测试的重复源码。
