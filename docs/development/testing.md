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

## 2. 验证分两档

按开发节奏分两档：做的过程中只跑聚焦验证，用户交给的整个任务做完时跑一次完整检查。中途不跑全量。

### 聚焦验证：开发中与每次提交前

适用于 Red / Green、局部功能开发、单个 Service、Controller、Vue 组件、缺陷修复，以及每次提交前。只运行：

- 新增或修改的测试；
- 直接受影响的相关测试；
- 必要的单个 Integration 测试（例如改动涉及真实数据库约束时）；
- 改动涉及前端类型或模板时的类型检查与 ESLint。

目标是几秒到几分钟。中途不跑 backend 全量、frontend 全量、全部 Testcontainers、Playwright、镜像构建或 Docker
全环境；本轮改动涉及鉴权、迁移、共享请求层等高风险能力、聚焦测试覆盖不到时，可加跑对应模块的测试，仍不跑全套。

临时浏览器检查复用[工作流中已核对的开发服务](workflow.md#运行环境与服务复用)，按页面影响选择
学习端或管理端地址；不因基础服务在 Docker 中就自动运行 `app-up` 或正式 E2E。

### 完整检查：整个任务做完时

用户交给的整个任务做完、准备交付时，跑一次[完整检查](#10-运行命令)。它的内容与 CI 完全相同，
包括集成测试、E2E 与镜像构建，不按风险删减，也不把其中任何一部分留给推送后的 CI。
发现问题修复后再跑，直到一次全部通过；推送前必须有当前提交的完整检查通过记录。

## 3. 昂贵验证的重复执行规则

如果当前工作区状态已经通过某项昂贵验证，之后只修改了与其无关的内容，不要再次运行。

- 完整 Playwright 已通过，之后只修改文档：不跑 Playwright。
- backend clean verify 已通过，之后只修改前端 CSS：不跑 backend。
- 完整 frontend 已通过，之后只修改后端一个独立单测：不重新跑全部 frontend。

不要形成“修改 → 全量测试 → 修改 → 全量测试”的循环：开发中只用聚焦验证，任务做完跑一次完整检查。

## 5. 必须新增测试的情况

- 修改判分、考试提交、错题归集、权限或限流规则。
- 修复线上或验收中发现的缺陷，需要回归用例防止复发。
- 新增参数校验、异常码、事务或并发控制。
- 新增复杂前端状态、路由权限、流式响应解析或安全过滤逻辑。
- 修改公共接口契约、数据库约束或迁移。
- 修改 AI 结构化输出解析、私有答案隔离或真实完成事件。

以上场景默认先编写能够复现目标行为的失败测试，确认失败原因正确后再实现。若受外部环境、纯配置兼容或其他客观条件限制而无法形成有效 Red，必须在提交或交付总结中记录原因与替代验证；
仍影响当前判断时同时更新项目状态。

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
登录准备统一调用 `e2e/helpers/turnstile.ts`：只拦截浏览器端 Cloudflare SDK 请求，按
[官方测试密钥协议](https://developers.cloudflare.com/turnstile/troubleshooting/testing/)返回 dummy token，
避免公网脚本和挑战 iframe 下载波动阻塞业务回归。实际验证码组件、回调、登录请求和后端
Cloudflare Siteverify 校验保持真实执行；E2E 验证缺失验证码和错误密码仍被拒绝。测试密钥的
always-pass 响应不验证真实 token 的有效性，Siteverify 失败响应由后端专项测试覆盖；此夹具
不覆盖 Cloudflare 浏览器挑战页面或真实凭据闭环。Playwright 默认零重试，首次失败保留 trace。
题目理解主线的已审核状态由 `e2e/fixtures/understanding/` 的隔离 Flyway 夹具提供，复用后端
集成测试的数据准备方式；它只在 E2E Profile 和 Compose 中加载，不改生产种子的待审核状态，
也不构成人工内容审核。夹具属于运行镜像指纹输入，变更后须重建隔离环境。
E2E Compose 还提供 OpenAI 兼容的确定性上游响应，只替代不可重复且需要密钥的外部模型调用；后端的
Prompt 构造、HTTP Provider、结构校验、调用审计、草稿状态和数据库事务仍使用真实实现。
功能 E2E 的 AI 日配额设为 0（不限次数），避免并发用例及重试共用额度；配额拒绝由后端专项测试验证。
模拟上游不得在开发或生产 Compose 中启用，也不能替代 AI 解析与异常分支的单元测试。

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

反复修改测试脚本时，可保留隔离环境并让下一轮经脚本校验后复用：

```bash
npm run test:e2e -- --keep --grep '匿名未知地址'
python3 ../scripts/docker-lifecycle.py e2e-clean
```

`--keep` 必须放在 Playwright 参数前；它只控制生命周期，不会传给 Playwright。脚本检查
`learnplatform-e2e` 的所有服务正在运行、带健康检查的服务已健康，并核对前后端运行容器所用镜像及镜像的当前源码
指纹。全部满足时跳过构建与容器重建，使用已有前端映射端口运行 Playwright；否则输出原因、
清除旧 E2E 数据卷并完整重建。`e2e-clean` 删除保留的 E2E 容器、数据卷与两份应用镜像。

前端 Dockerfile 的 `COPY . .` 和 `.dockerignore` 当前没有排除 `e2e/` 或
`playwright.config.ts`；它们进入构建阶段，但纯 Playwright TypeScript 测试代码和配置不进入
最终运行容器，也不参与 `npm run build` 的产物。E2E 运行镜像指纹排除这些代码和配置；
Compose 绑定挂载的 E2E 输入仍计入指纹，包括 `ai-mock.py` 和 `fixtures/knowledge/`，
改动后须完整重建。单改未挂载的测试代码不会触发重建；
日常 `app-up` 的指纹仍覆盖完整前端构建上下文。复用的数据库保留上一轮测试写入的数据，
只用于应用源码未变、反复调整测试的验证。判断交付前，以完整检查中一次不带 `--keep` 的
完整重建为准。

不带 `--keep` 时，脚本清除旧 E2E 数据卷，使用当前前后端源码构建镜像、强制重建并等待
所有服务运行且带健康检查的服务健康，然后运行 Playwright。成功、测试失败或启动异常都会在 `finally` 中按明确的
`learnplatform-e2e` 范围删除 E2E 容器、数据卷和两份应用镜像，再按
[Docker 磁盘增长治理](docker-disk-governance.md)回收悬空镜像与超预算缓存。若默认 18000
端口被占用，脚本选择空闲端口并同步设置 Playwright Base URL。

`npm run test:e2e:playwright` 只是生命周期脚本内部使用的 Playwright 命令；Agent 不得单独
执行它并把已有容器的结果记作当前源码 E2E。复用必须通过上述入口的指纹校验。

## 9. Agent 临时浏览器流程验收

临时浏览器检查的流程、取证与失败排查由项目
[frontend-flow-test](../../.agents/skills/frontend-flow-test/SKILL.md)维护。它不替代自动化回归；
发现缺陷后按本页的风险标准补充测试，发布、演示或共享能力变化优先复用现有自动化验证。

## 10. 运行命令

完整检查与 CI 共用入口，默认顺序执行所有 job，任一步失败立即停止：

```bash
python3 scripts/check-all.py
```

按 CI 的 job 名只跑一部分，可指定多个：

```bash
python3 scripts/check-all.py docs
python3 scripts/check-all.py backend backend-integration
python3 scripts/check-all.py frontend
python3 scripts/check-all.py docker e2e
```

job 名为 `docs`、`backend`、`backend-integration`、`frontend`、`docker`、`e2e`。
本机需准备 JDK 21、Node.js 22、Docker 和 Playwright Chromium；Maven 不在 PATH 时自动使用
仓库 Wrapper。脚本统一设置 `CI=true`，使 Playwright 的 `.only` 门禁与报告配置也与 CI 一致；本地与 CI 均默认零重试。
CI 保留工具安装、缓存与浏览器准备，检查命令统一由脚本维护。
后端测试默认将进程 `TZ` 与测试 JVM 的 `user.timezone` 都设为 UTC，以检查其是否独立于纽约业务时区。
日期夹具使用共享业务时钟或固定 `Clock`；集成测试的 JDBC 连接时区与生产一致，MySQL 会话仍采用业务时区。
可用以下命令复验机器时区和执行顺序：

```bash
python3 scripts/check-all.py backend backend-integration --backend-order reversealphabetical
python3 scripts/check-all.py backend backend-integration --backend-timezone America/New_York --backend-order random --backend-seed 20261018
```

`alphabetical`（默认）和 `reversealphabetical` 控制测试类顺序；`random` 同时打乱类与方法顺序，
固定种子用于复现。命令仍执行原有完整 job，不跳过断言或开启重试。

E2E 使用正式 `npm run test:e2e` 入口，重建并清理隔离环境，不带 `--keep`；
脚本设置隔离测试凭据并禁止 Compose 自动加载 `.env`。
CI 用 `e2e --step dependencies` 和 `e2e --step tests` 在安装依赖与执行测试之间准备 Chromium。

本地提交信息检查范围为 `origin/main..HEAD`；CI 传入事件的 `BASE_SHA`、`HEAD_SHA` 与
`FORCED_PUSH`，并保留 Dependabot PR 的提交格式豁免。

只有全部 job 通过、检查前后工作区均无未提交改动且 HEAD 未改变，脚本才将该 HEAD 写入
Git common dir 的 `check-all-passed`（worktree 共用）。部分检查、跳过提交信息检查或失败均不写入；
开始完整检查时会清除旧记录。提交或 rebase 后须对新的 HEAD 完整检查。

安装本地钩子：`cp scripts/hooks/pre-push "$(git rev-parse --git-common-dir)/hooks/pre-push"`。
钩子保留 `CODEX_AUTONOMOUS=1` 拦截，并逐条核对推送引用的提交与记录完全一致；删除远端引用除外，
钩子只查记录，不重跑测试，也不修改 Git 配置。

默认后端单测排除 `@Tag("integration")`；单独验证某个真实 MySQL 集成测试类可以使用：

```bash
cd backend
./mvnw test -DexcludedGroups= -Dtest=WrongQuestionServiceIntegrationTest
./mvnw test -DexcludedGroups= -Dtest=AiVariantTrainingIntegrationTest
```

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
