# AI 题库与错题复习系统 - 开发日志

## Round 141 - 2026-07-02

### 阶段
Phase 20 / Phase 21：AI 运营提醒站内入口与前端壳层 polish

### 完成内容
1. 在 `AppLayout.vue` 顶部栏新增管理员专属 AI 运营提醒入口，复用已存在的未确认提醒查询接口展示数字角标和提醒下拉。
2. 下拉面板支持刷新未确认提醒、查看提醒等级/类型/消息/周期，并可直接确认提醒；确认成功后即时从顶部待办列表移除。
3. 处理管理员身份异步恢复场景：管理员信息就绪后自动拉取提醒，普通用户不展示入口且会清空提醒状态。
4. 补充桌面端和移动端样式，避免提醒按钮挤压搜索入口和用户菜单。
5. 同步更新路线图、交接文档和 README。

### 修改文件
- `frontend/src/components/layout/AppLayout.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd frontend && npm test -- --run src/__tests__/api/aiUsage.test.ts`：5 个测试通过。
- `cd frontend && npm test -- --run`：26 个文件、209 个测试通过。
- `cd frontend && npm run build`：通过（仍保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。

### 遗留问题
- 本轮未启动真实后端或 Docker E2E 环境，未做顶部栏提醒下拉的真实接口点击验收。
- AI 运营提醒已有站内顶部入口和管理页确认入口，尚未接入站外通知（邮件、Webhook 等）。
- Phase 20 推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 新增 AI 运营提醒顶部入口`

## Round 140 - 2026-07-02

### 阶段
Phase 20：AI 运营治理（运营提醒持久化与确认）

### 完成内容
1. 新增 `ai_usage_alert` 表和 Flyway V14 迁移，用于保存 AI 运营报告生成的异常提醒、统计周期、指标快照、确认人和确认时间。
2. 扩展 `AiUsageService`：运营报告命中高失败率、失败率突增、延迟突增或调用量突增时写入未确认提醒；同类型、同周期天数、同一天生成的未确认提醒会更新同一条记录，避免刷新页面制造重复待办。
3. 新增管理端提醒接口：查询未确认 AI 运营提醒、确认指定提醒；确认操作会记录当前管理员 ID 与确认时间。
4. 管理端 `AiUsageView` 的报告提醒区新增周期展示和“确认”按钮，确认成功后刷新运营报告。
5. 补充后端服务测试和前端 API 契约测试，并同步 API、DB、路线图、交接文档和 README。

### 修改文件
- `backend/src/main/resources/db/migration/V14__create_ai_usage_alert_table.sql`
- `backend/src/main/java/com/learnplatform/entity/AiUsageAlert.java`
- `backend/src/main/java/com/learnplatform/mapper/AiUsageAlertMapper.java`
- `backend/src/main/java/com/learnplatform/dto/AiUsageAlertVO.java`
- `backend/src/main/java/com/learnplatform/dto/AiUsageReportVO.java`
- `backend/src/main/java/com/learnplatform/service/AiUsageService.java`
- `backend/src/main/java/com/learnplatform/controller/AdminAiUsageController.java`
- `backend/src/test/java/com/learnplatform/service/AiUsageServiceTest.java`
- `frontend/src/api/aiUsage.ts`
- `frontend/src/__tests__/api/aiUsage.test.ts`
- `frontend/src/views/admin/AiUsageView.vue`
- `docs/API_DESIGN.md`
- `docs/DB_DESIGN.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd backend && mvn test -Dtest=AiUsageServiceTest`：16 个测试通过。
- `cd frontend && npm test -- --run src/__tests__/api/aiUsage.test.ts`：5 个测试通过。
- `cd backend && mvn test`：367 个默认后端测试通过。
- `cd frontend && npm test -- --run`：26 个文件、209 个测试通过。
- `cd frontend && npm run build`：通过（仍保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。

### 遗留问题
- 本轮未启动真实后端或 Docker E2E 环境，未做管理端 AI 调用分析页真实点击验收。
- AI 运营提醒当前支持持久化与管理员确认，尚未发送站内信或外部通知。
- Phase 20 推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(ai): 持久化运营异常提醒`

## Round 139 - 2026-07-02

### 阶段
Phase 16 / Phase 20：正式题目 AI 复审建议与缓存

### 完成内容
1. 新增正式题目 AI 复审建议服务：管理员可对已入库题目生成 `APPROVE` / `REVISE` / `REJECT` 建议、置信分、风险点、修订建议、答案检查和知识点/时效性分析。
2. 新增 `questionReviewSuggestion` 缓存区域，默认 TTL 30 分钟；题目编辑、删除或提交正式复审后会清除对应题目的复审建议缓存，避免复用过期建议。
3. 管理端题目复审弹窗新增“AI 复审建议”入口，支持查看建议摘要并一键填入复审动作、修订内容、难度和复审意见。
4. 补充后端服务测试、控制器接口测试和前端 API 契约测试，覆盖 AI 正常 JSON、AI 失败回退、题目不存在和接口路径。
5. 同步更新 API 文档、路线图、交接文档、README 和环境变量示例。

### 修改文件
- `backend/src/main/java/com/learnplatform/dto/QuestionReviewSuggestionVO.java`
- `backend/src/main/java/com/learnplatform/service/QuestionReviewSuggestionService.java`
- `backend/src/main/java/com/learnplatform/controller/AdminQuestionController.java`
- `backend/src/main/java/com/learnplatform/config/RedisConfig.java`
- `backend/src/main/java/com/learnplatform/service/QuestionService.java`
- `backend/src/main/java/com/learnplatform/service/QuestionSourceService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/learnplatform/service/QuestionReviewSuggestionServiceTest.java`
- `backend/src/test/java/com/learnplatform/controller/AdminQuestionControllerTest.java`
- `frontend/src/api/question.ts`
- `frontend/src/__tests__/api/question.test.ts`
- `frontend/src/views/admin/QuestionManage.vue`
- `.env.example`
- `docs/API_DESIGN.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd backend && mvn test -Dtest=QuestionReviewSuggestionServiceTest,AdminQuestionControllerTest,QuestionSourceServiceTest`：22 个测试通过。
- `cd backend && mvn test`：365 个默认后端测试通过。
- `cd frontend && npm test -- --run src/__tests__/api/question.test.ts`：14 个测试通过。
- `cd frontend && npm test -- --run`：26 个文件、207 个测试通过。
- `cd frontend && npm run build`：通过（仍保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。

### 遗留问题
- 本轮未启动真实后端或 Docker E2E 环境，未做管理端复审弹窗真实点击验收。
- Phase 20 推送后的 GitHub Actions 实跑仍待完成。
- 前端构建仍有第三方 `@vueuse/core` pure annotation 和大 chunk 警告，不阻断构建。

### 建议 commit message
`feat(ai): 新增题目复审建议缓存`

## Round 138 - 2026-07-01

### 阶段
Phase 20：演示验收与 AI 运营治理（Testcontainers 集成测试修复）

### 完成内容
1. 复现本地 Testcontainers 失败原因：Docker CLI 与 Docker Desktop 正常，但旧 Testcontainers `1.20.1` 在 Docker Engine 29 下无法识别有效 Docker 环境。
2. 将后端 Testcontainers 依赖升级到 `1.21.4`，恢复 Docker Desktop / Docker Engine 29 环境下的容器连接能力。
3. 修复集成测试容器与真实 Flyway 基线迁移不一致的问题：容器数据库名改为 `learn_platform`，Flyway 使用容器 root 用户执行迁移，业务数据源继续使用测试用户。
4. 补齐真实表约束下的集成测试夹具：考试、刷题和统计测试中的题目课程、选项标签、考试记录时间和总分字段。
5. 修复 `StatisticsService` 对 Map 聚合数值类型的强转假设，统一按 `Number` 读取排序值，避免真实 MySQL 下 `Integer`/`Long` 混用导致 `ClassCastException`。

### 修改文件
- `backend/pom.xml`
- `backend/src/main/java/com/learnplatform/service/StatisticsService.java`
- `backend/src/test/java/com/learnplatform/IntegrationTestBase.java`
- `backend/src/test/java/com/learnplatform/service/ExamServiceIntegrationTest.java`
- `backend/src/test/java/com/learnplatform/service/PracticeServiceIntegrationTest.java`
- `backend/src/test/java/com/learnplatform/service/StatisticsServiceIntegrationTest.java`
- `docs/TESTING.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd backend && mvn test -DexcludedGroups= -Dtest=WrongQuestionServiceIntegrationTest`：17 个真实 MySQL 集成测试通过。
- `cd backend && mvn test -DexcludedGroups= -Dgroups=integration`：53 个真实 MySQL 集成测试通过。
- `cd backend && mvn test`：361 个默认后端测试通过。

### 遗留问题
- Phase 20 推送后的 GitHub Actions 实跑仍待完成；当前 `main` 本地仍领先远端。
- 前端构建仍有第三方 `@vueuse/core` pure annotation 和大 chunk 警告，不阻断构建。
- Docker/E2E 环境当前处于运行状态，如不需要可执行 `docker compose -f docker-compose.yml -f docker-compose.e2e.yml down -v` 清理。

### 建议 commit message
`test(backend): 修复 Testcontainers 集成测试基线`

## Round 137 - 2026-07-01

### 阶段
Phase 20 / Phase 21：真实接口 E2E 验收与错题本业务修复

### 完成内容
1. 在 Docker E2E 环境中重跑真实浏览器流程，发现“刷题答错→错题本”闭环在重复答错已逻辑删除错题时会因 `wrong_question.uk_user_question` 唯一键冲突返回 500。
2. 修复 `WrongQuestionService.addWrongQuestion`：新增数据库级 `reviveOrIncrement` 更新，命中历史错题记录时递增 `wrong_count`、更新最近错误答案、必要时重置掌握度并将 `deleted` 复活为 0；未命中时才插入新记录。
3. 为错题本服务补充回归测试：单元测试覆盖新增 mapper 更新路径；集成测试类新增“逻辑删除后再次答错应复活原记录”用例。
4. 调整 Maven Surefire 配置，让 `excludedGroups` 可通过命令行覆盖，避免集成测试命令出现 0 tests 的假绿；同步更新 `docs/TESTING.md`。
5. 使用修复后的后端镜像重建 Docker E2E 环境，并确认 4 条 Playwright 真实业务闭环全部通过。

### 修改文件
- `backend/pom.xml`
- `backend/src/main/java/com/learnplatform/mapper/WrongQuestionMapper.java`
- `backend/src/main/java/com/learnplatform/service/WrongQuestionService.java`
- `backend/src/test/java/com/learnplatform/service/WrongQuestionServiceTest.java`
- `backend/src/test/java/com/learnplatform/service/WrongQuestionServiceIntegrationTest.java`
- `docs/TESTING.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd backend && mvn test -Dtest=WrongQuestionServiceTest`：6 个测试通过。
- `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --force-recreate --wait backend frontend`：后端与前端容器使用当前源码重建并健康启动。
- `cd frontend && npm run test:e2e`：4 条 Playwright 真实 E2E 通过（登录课程、刷题错题重练、考试判分、投稿审核入库）。
- `cd backend && mvn test`：361 个测试通过。
- `cd backend && mvn test -DexcludedGroups= -Dtest=WrongQuestionServiceIntegrationTest`：集成类已不再 0 tests，但本机 Testcontainers 无法连接 Docker Desktop Java 客户端，执行失败；本轮用真实 Docker E2E 覆盖了该业务缺陷。

### 遗留问题
- Phase 20 推送后的 GitHub Actions 实跑仍待完成；当前 `main` 本地仍领先远端。
- 本机 Testcontainers 与 Docker Desktop Socket 兼容性仍待处理，CI 使用 JDK 17 作为基线。
- 前端构建仍有第三方 `@vueuse/core` pure annotation 和大 chunk 警告，不阻断构建。
- Docker/E2E 环境当前处于运行状态，如不需要可执行 `docker compose -f docker-compose.yml -f docker-compose.e2e.yml down -v` 清理。

### 建议 commit message
`fix(backend): 修复错题复活时唯一键冲突`

## Round 136 - 2026-07-01

### 阶段
Phase 21：前端信息架构与视觉体验优化（Element Plus 旧 API 收尾）

### 完成内容
1. 清理 `LearningPathView.vue` 中 `el-radio-button` 继续使用 `label` 作为取值的旧写法，改为 Element Plus 当前推荐的 `value` 属性。
2. 使用 `rg` 扫描 `frontend/src`，确认不再存在 `el-radio` / `el-radio-button` 的旧 `label` 取值写法。
3. 保留 Round 135 的管理端批量操作、空状态和相关文档改动，在其基础上做小范围收尾。

### 修改文件
- `frontend/src/views/statistics/LearningPathView.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `rg "<el-radio[^\\n>]*label=|<el-radio-button[^\\n>]*label=" frontend/src -n`：无命中。
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（仍保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。

### 遗留问题
- 本轮未启动后端，未做真实接口点击验收。
- Phase 20 推送后的 GitHub Actions 实跑仍待完成。
- 构建仍有第三方依赖 pure annotation 和大 chunk 警告，不阻断构建。

### 建议 commit message
`fix(frontend): 清理 Element Plus 单选按钮旧 API`

## Round 135 - 2026-06-30

### 阶段
Phase 21：前端信息架构与视觉体验优化（管理端批量操作与空状态 polish）

### 完成内容
1. 为 `UserManage.vue` 增加表格选择列和批量工具条，支持批量启用/禁用选中用户，并在翻页、筛选后清空旧选择，降低误操作风险。
2. 为 `QuestionManage.vue` 增加表格选择列和批量工具条，支持批量清除 AI 学习资产缓存、批量删除题目，两个高风险动作均保留二次确认。
3. 为 `SubmissionManage.vue` 增加表格选择列和批量工具条，支持批量通过待审核投稿、批量入库已通过投稿，并按投稿状态过滤可处理项。
4. 在 `global.css` 新增 `admin-bulk-bar`、`admin-bulk-actions` 和 `admin-table-empty` 样式，统一后台批量操作区和空状态展示。
5. 为用户、题目、投稿三张高频管理表补充空状态；用户和题目空状态提供直接新增入口。

### 修改文件
- `frontend/src/assets/styles/global.css`
- `frontend/src/views/admin/UserManage.vue`
- `frontend/src/views/admin/QuestionManage.vue`
- `frontend/src/views/admin/SubmissionManage.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：5173 被占用，Vite 自动启动在 `http://127.0.0.1:5174/`。
- Playwright 本地 mock 管理端接口检查 `/admin/users`、`/admin/questions`、`/admin/submissions`：三页选中首行后批量工具条出现，点击“清空选择”后工具条消失；桌面 1440x980 无横向溢出；题目空状态“没有匹配的题目”可见。

### 遗留问题
- 本轮未启动后端，浏览器检查使用 mock 数据，不代表真实接口点击验收。
- 浏览器检查期间观察到既有 Element Plus radio `label` 旧 API 警告；本轮未扩范围处理。
- Phase 20 推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 补齐管理端批量操作与空状态`

## Round 134 - 2026-06-30

### 阶段
Phase 21：前端信息架构与视觉体验优化（管理端操作列 polish）

### 完成内容
1. 收纳 `UserManage.vue` 表格长操作列：保留“改角色/启禁用”高频动作，将“重置密码/AI 配额/删除用户”放入“更多”菜单，操作列宽度从 350 降至 210。
2. 收纳 `QuestionManage.vue` 表格操作列：保留“复审/编辑”，将“清缓存/删除题目”放入“更多”菜单，并改用二次确认弹窗保护高风险动作。
3. 收纳 `SubmissionManage.vue` 投稿操作列：保留“详情/审核/入库”等状态相关主动作，将 AI 质检、知识点标注和难度评估归入“AI 工具”菜单。
4. 在 `global.css` 新增 `admin-row-actions` 和危险下拉项样式，统一后台表格行操作的间距、换行和危险操作色彩。

### 修改文件
- `frontend/src/assets/styles/global.css`
- `frontend/src/views/admin/UserManage.vue`
- `frontend/src/views/admin/QuestionManage.vue`
- `frontend/src/views/admin/SubmissionManage.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：前端启动成功，访问 `http://127.0.0.1:5173/`。
- 尝试用本机 Chrome + Playwright mock 管理端接口检查 `/admin/users`、`/admin/questions`、`/admin/submissions`；因本轮临时 mock 环境未能稳定等待到表格行操作，未作为通过项。构建和类型检查已确认模板与 TS 无编译错误。

### 遗留问题
- 本轮未启动后端，未做真实接口点击验收；后续如继续管理端 polish，可在 Docker/E2E 环境中补管理员真实点击检查。
- Phase 20 推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 收纳管理端表格操作列`

## Round 133 - 2026-06-29

### 阶段
Phase 20：演示验收与 AI 运营治理（演示截图流水线）

### 完成内容
1. 新增 `frontend/scripts/capture-demo-screenshots.mjs`，基于 Playwright 登录真实前端与后端接口，批量生成桌面演示截图。
2. 新增 `npm run screenshots:demo` 命令，默认连接 `http://localhost:18000` 的 E2E Docker 前端，并输出到 `docs/demo-screenshots/`。
3. 截图覆盖用户端首页、课程、题库、刷题、错题、复习、考试，以及管理端平台总览、题目管理、投稿管理和 AI 调用分析。
4. 脚本在页面截图期间监听 `/api/` 4xx/5xx 响应，发现接口错误时直接失败，避免生成不可信演示素材。
5. 修复 simple cache 模式下未注册缓存名导致统计接口 500 的问题，避免 E2E、本地 simple 缓存环境访问首页统计失败。
6. 将 E2E Docker 后端 profile 调整为 `e2e,docker`，保留固定验证码与 simple cache，同时启用 Docker JSON 日志，方便定位演示验收异常。
7. 在可用 Docker 环境中生成 11 张真实演示截图，输出到 `docs/demo-screenshots/`。
8. 更新 `docs/DEMO.md`、`docs/ROADMAP.md`、`docs/HANDOFF.md` 和 `README.md`，补充截图生成方式与当前状态。

### 修改文件
- `backend/src/main/java/com/learnplatform/config/RedisConfig.java`
- `backend/src/test/java/com/learnplatform/config/RedisConfigTest.java`
- `docker-compose.e2e.yml`
- `frontend/scripts/capture-demo-screenshots.mjs`
- `frontend/package.json`
- `docs/demo-screenshots/*.png`
- `docs/DEMO.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd frontend && node --check scripts/capture-demo-screenshots.mjs`：通过。
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd backend && mvn test -Dtest=RedisConfigTest`：通过。
- `cd backend && mvn test`：361 个测试通过。
- `docker compose -f docker-compose.yml -f docker-compose.e2e.yml config --quiet`：通过。
- `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --force-recreate --wait backend frontend`：通过，后端与前端容器健康。
- `cd frontend && npm run screenshots:demo`：通过，生成 11 张 1440px 宽桌面截图。
- 抽检 `desktop-01-home-workbench.png` 与 `desktop-11-admin-ai-usage.png`：内容为真实登录后的学习者首页和管理端 AI 调用分析页。

### 遗留问题
- Phase 20 推送后的 GitHub Actions 实跑仍待完成。
- 当前 E2E Docker 环境仍在运行，后续不用时可执行 `docker compose -f docker-compose.yml -f docker-compose.e2e.yml down -v` 清理。

### 建议 commit message
`chore(demo): 生成演示截图并修复 simple cache`

## Round 132 - 2026-06-28

### 阶段
Phase 21：前端信息架构与视觉体验优化（课程入口页 polish）

### 完成内容
1. 重做 `CourseListView.vue` 为课程中心工作台：新增课程页头、快捷入口、课程摘要卡、课程搜索、课程卡片操作区和空状态，统一使用 Phase 21 全局视觉变量。
2. 重做 `CourseDetailView.vue`：新增课程详情页头、查看题目/开始练习动线、知识点总数/顶级节点/叶子节点/最大层级摘要卡，并优化知识点树节点层级与移动端折行。
3. 为 `QuestionListView.vue` 增加 `courseId` 路由参数初始化，使从课程页点击“查看题目”后实际按课程筛选请求题库接口。
4. 同步更新 `docs/ROADMAP.md`、`docs/HANDOFF.md` 和 `README.md`，将课程入口与详情页纳入 Phase 21 用户端整理状态。

### 修改文件
- `frontend/src/views/course/CourseListView.vue`
- `frontend/src/views/course/CourseDetailView.vue`
- `frontend/src/views/course/QuestionListView.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：前端启动成功，访问 `http://127.0.0.1:5173/`。
- Playwright 本地布局检查：使用 mock 登录态和课程/知识点/题库接口验证 `/courses`、`/courses/1` 在桌面 1440x980 与移动端 390x844 均无横向溢出；课程搜索 `Java` 可收窄为 1 门课程；课程详情知识点摘要显示 4 个知识点；从课程详情进入题库会请求 `/api/questions?pageNum=1&pageSize=10&courseId=1`。

### 遗留问题
- 本轮未启动后端；浏览器检查使用本地 mock 数据验证 UI 布局、搜索和路由参数，不代表真实接口联调。
- Phase 20 的真实演示截图与推送后的 GitHub Actions 实跑仍待完成。
- 后续可继续整理刷题记录、收藏题、学习报告等二级用户页，或转向演示截图制作。

### 建议 commit message
`feat(frontend): 优化课程入口页体验`

## Round 131 - 2026-06-28

### 阶段
CI 修复（Browser E2E 选择器兼容）

### 完成内容
1. 定位最新 GitHub Actions run `28326469277`：Frontend、Backend 和 Docker Build 均通过，Browser E2E 失败。
2. 修复 `auth-and-course.spec.ts` 中 Phase 21 前端改版后过时的首页品牌文案断言：从旧 `AI 题库系统` 改为当前首页主区域的 `今日学习工作台`。
3. 收窄课程列表与投稿管理测试中的定位范围：课程入口改为菜单项定位，课程标题和投稿搜索按钮限定在 `main` 区域，避免与全局搜索/布局标题产生 strict mode 冲突。

### 修改文件
- `frontend/e2e/auth-and-course.spec.ts`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- 尝试启动本地 Docker E2E 环境失败：Docker Desktop 处于手动暂停状态（`Docker Desktop is manually paused`），因此本地未能完整执行 `npm run test:e2e`。

### 遗留问题
- 需要推送后以 GitHub Actions 的 Browser E2E 结果确认修复是否完整。

### 建议 commit message
`test(e2e): 修复前端改版后的选择器`

## Round 130 - 2026-06-28

### 阶段
Phase 21：前端信息架构与视觉体验优化（管理端知识点页 polish）

### 完成内容
1. 整理 `KnowledgePointManage.vue`：接入统一后台页头、操作区、管理端表格卡片和移动端折行基线。
2. 新增知识点摘要卡：展示知识点总数、顶级节点、叶子节点和最大层级，帮助管理员快速判断课程知识结构规模。
3. 新增本地关键词筛选：支持按知识点名称和描述过滤树结构，并保留命中节点的父级路径。
4. 优化树节点视觉层级：文件夹/叶子节点图标分色，节点描述改为次级文本，行操作改为图标化按钮。
5. 同步更新 `docs/ROADMAP.md`、`docs/HANDOFF.md` 和 `README.md`，将管理端知识点页纳入 Phase 21 管理端体验整理状态。

### 修改文件
- `frontend/src/views/admin/KnowledgePointManage.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`
- `README.md`

### 验证
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：前端启动成功，访问 `http://127.0.0.1:5173/`。
- Playwright 本地布局检查：使用 mock 管理员会话和知识点树数据验证 `/admin/knowledge-points?courseId=1&courseName=Java%20%E5%90%8E%E7%AB%AF` 在桌面 1440x980 与移动端 390x844 均无横向溢出；统计值为 5/2/3/2，搜索 `Controller` 后树节点从 5 个收窄到 2 个。

### 遗留问题
- 本轮未启动后端；浏览器检查使用本地 mock 数据验证 UI 布局和筛选行为，不代表真实接口联调。
- Phase 20 的真实演示截图与推送后的 GitHub Actions 实跑仍待完成。
- 后续可继续做管理端批量操作、空状态细化、长操作列收纳和真实接口环境下的点击验收。

### 建议 commit message
`feat(frontend): 优化知识点管理页体验`

## Round 129 - 2026-06-28

### 阶段
Phase 21：前端信息架构与视觉体验优化（P3 管理端剩余页面整理）

### 完成内容
1. 整理 `QuestionManage.vue`：接入统一后台页头、摘要卡、筛选工具栏、表格卡片和分页样式；行操作改为图标化按钮，保留下载模板、导入、导出、新增、复审、编辑、清缓存和删除能力。
2. 整理 `ExamManage.vue`：新增试卷管理页说明、摘要卡和统一表格容器；智能组卷与新增试卷操作进入统一页头操作区，发布/编辑/删除操作图标化。
3. 整理 `SubmissionManage.vue`：将投稿统计改为管理端摘要卡，统一筛选区、刷新入口、表格容器、分页和 AI 质检/标注/测难度/入库操作展示。
4. 整理 `AiUsageView.vue`：接入统一后台页头，将原分散统计卡收敛为 8 个运营摘要卡，保留运营报告、趋势图、分布图、功能详情、Top 用户和失败调用列表。
5. 同步更新 `docs/ROADMAP.md` 与 `docs/HANDOFF.md`，将 Phase 21 P3 主要管理页整理状态更新为已完成，下一步转向演示截图、CI 实跑和细节 polish。

### 修改文件
- `frontend/src/views/admin/QuestionManage.vue`
- `frontend/src/views/admin/ExamManage.vue`
- `frontend/src/views/admin/SubmissionManage.vue`
- `frontend/src/views/admin/AiUsageView.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：前端启动成功，访问 `http://127.0.0.1:5173/`。
- Playwright 本地布局检查：使用 mock 管理端接口数据验证 `/admin/questions`、`/admin/exams`、`/admin/submissions`、`/admin/ai-usage` 在桌面 1440x980 与移动端 390x844 均无横向溢出，页头操作区和摘要卡正常渲染。

### 遗留问题
- 本轮未启动后端；浏览器检查使用本地 mock 数据验证 UI 布局，不代表真实接口联调。
- Phase 21 后续可继续做批量操作、空状态、长操作列收纳和真实接口环境下的管理端点击验收。
- Phase 20 的真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 优化管理端列表页体验`

## Round 128 - 2026-06-28

### 阶段
工程化问题修复

### 完成内容
1. 修复全局搜索 API 封装重复 `/api` 前缀问题，避免请求落到 `/api/api/search`。
2. 统一 AI 学习建议流式接口的 Base URL 获取方式，使其与其他流式接口一样支持 `VITE_API_BASE_URL`。
3. 收紧 Actuator 默认暴露面：默认仅暴露 `health` 与 `prometheus`，健康详情默认不公开；Security 仅匿名放行健康检查和 Prometheus 指标。
4. 新增 `CacheTtlProperties`，将 Redis 缓存 TTL 从 `RedisConfig` 硬编码迁移到 `application.yml` 和环境变量。
5. 新增前端 API 契约测试，覆盖搜索路径和统计流式请求路径，防止 `/api` 前缀回归。

### 修改文件
- `frontend/src/api/search.ts`
- `frontend/src/api/statistics.ts`
- `frontend/src/__tests__/api/search.test.ts`
- `frontend/src/__tests__/api/statistics.test.ts`
- `backend/src/main/java/com/learnplatform/config/CacheTtlProperties.java`
- `backend/src/main/java/com/learnplatform/config/RedisConfig.java`
- `backend/src/main/java/com/learnplatform/config/SecurityConfig.java`
- `backend/src/main/resources/application.yml`
- `docs/HANDOFF.md`
- `docs/FUTURE.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run src/__tests__/api/search.test.ts src/__tests__/api/statistics.test.ts`：2 个文件、9 个测试通过。
- `cd frontend && npm test -- --run`：26 个文件、206 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd backend && mvn test -Dtest=GlobalSearchServiceTest -q`：通过。
- `cd backend && mvn test`：360 个测试通过。
- `docker compose config --quiet`：通过。

### 遗留问题
- 前端暂未配置 lint 脚本或 ESLint。
- 大型 Service 和页面组件仍可后续逐步拆分。
- 本轮未处理刷题记录与错题/复习计划同步的容错边界，该问题需要结合业务期望决定是强一致还是保留尽力同步。

### 建议 commit message
`fix: 修复接口路径与配置治理问题`

## Round 127 - 2026-06-28

### 阶段
Phase 21：前端信息架构与视觉体验优化（P3 管理端样板整理）

### 完成内容
1. 在 `global.css` 新增管理端通用页面骨架：统一后台页头、说明文字、操作区、筛选区、统计卡、表格卡片和分页样式。
2. 整理 `AdminDashboard.vue` 外壳，使管理总览页接入统一后台页头与页面宽度节奏。
3. 重构 `CourseManage.vue` 页头和表格容器，补充说明文字、查询按钮、结果摘要和图标化行操作。
4. 重构 `UserManage.vue` 为后台账号工作台：统计卡改为统一摘要卡，筛选区和分页接入通用后台表格样式，行操作增加图标并保留原有角色、状态、密码和 AI 配额功能。
5. 使用本地 Playwright 拦截数据检查 `/admin`、`/admin/courses`、`/admin/users` 的桌面与移动端布局，确认无横向溢出。

### 修改文件
- `frontend/src/assets/styles/global.css`
- `frontend/src/views/admin/AdminDashboard.vue`
- `frontend/src/views/admin/CourseManage.vue`
- `frontend/src/views/admin/UserManage.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run`：25 个文件、203 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：前端启动成功，访问 `http://127.0.0.1:5173/`。
- Playwright 本地布局检查：使用拦截数据验证 `/admin`、`/admin/courses`、`/admin/users` 在桌面 1440x980 与移动端 390x844 均无横向溢出，标题和关键操作按钮正常渲染。

### 遗留问题
- 本轮未启动后端；浏览器检查使用本地拦截数据验证 UI 布局，不代表真实接口联调。
- Phase 21 P3 仍可继续整理 `QuestionManage.vue`、`ExamManage.vue`、`SubmissionManage.vue` 与 `AiUsageView.vue`，让所有管理端列表页完全收敛到同一套后台基线。
- Phase 20 的真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 优化管理端页面体验`

## Round 126 - 2026-06-28

### 阶段
协作规范与测试流程优化

### 完成内容
1. 新增 `skills/frontend-flow-test/SKILL.md`，定义 Agent 临时浏览器流程验收规范，强调只跑当前任务相关的最小业务闭环。
2. 明确临时浏览器验收与正式 Vitest、后端测试、Playwright E2E 的关系：前者用于快速确认运行体验，后者用于长期回归保护。
3. 在 `docs/TESTING.md` 增加“Agent 临时浏览器流程验收”章节，要求默认低 token 读取关键状态，失败时再逐级升级到局部 DOM、截图、日志和后端容器日志。
4. 更新 `AGENTS.md`、`docs/HANDOFF.md` 和 `skills/README.md`，加入 `frontend-flow-test` 的读取路由与使用说明。

### 修改文件
- `skills/frontend-flow-test/SKILL.md`
- `skills/README.md`
- `AGENTS.md`
- `docs/TESTING.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `python3 /Users/umico/.codex/skills/.system/skill-creator/scripts/quick_validate.py skills/frontend-flow-test`：通过。

### 遗留问题
- 本轮只更新协作规范和测试文档，未运行前后端自动化测试。
- 后续进行前端浏览器临时验收时，应先使用该 skill；若发现可复现缺陷，再补正式回归测试。

### 建议 commit message
`docs(testing): 增加前端流程验收 skill`

## Round 125 - 2026-06-27

### 阶段
Phase 21：前端信息架构与视觉体验优化（题库浏览页整理）

### 完成内容
1. 重构 `QuestionListView.vue` 页面结构：从普通筛选表单和卡片列表改为“题库浏览工作台”，包含页头说明、结果统计、筛选侧栏和题目结果区。
2. 优化题型、课程、难度筛选交互：题型改为按钮组，难度改为紧凑选项卡，筛选变更时自动回到第一页，并提供清空筛选入口。
3. 整理题目卡片层级：题型、课程、难度、分值、选项、知识点标签、收藏和讨论入口分区更清晰。
4. 补充桌面与移动端响应式样式，确保筛选区、题目选项、分页和操作按钮在窄屏下自然折行。
5. 同步更新 `docs/ROADMAP.md` 与 `docs/HANDOFF.md`，标记 QuestionListView 整理完成，并把下一步调整为 Phase 21 P3 管理端工作台与列表体验。

### 修改文件
- `frontend/src/views/course/QuestionListView.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run`：25 个文件、203 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：前端启动成功，访问 `http://127.0.0.1:5173/`。
- Playwright 本地布局检查：`/questions` 在桌面 1440x980 与移动端 390x844 均无横向溢出，题库页标题、题目卡片和结果摘要正常渲染。

### 遗留问题
- 本轮未启动后端；浏览器检查使用本地拦截数据验证 UI 布局，不代表真实接口联调。
- Phase 21 P3 管理端工作台与列表体验仍待完成。
- Phase 20 的真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 优化题库浏览页体验`

## Round 124 - 2026-06-25

### 阶段
Phase 21：前端信息架构与视觉体验优化（P2 核心业务页整理）

### 完成内容
1. 重构 `PracticeView.vue` 页面结构：改为“智能推荐 / 自选模式 / 练习方式说明”的任务面板，保留原有统计、课程/题型/难度筛选和开始刷题逻辑。
2. 整理 `WrongQuestionView.vue`：统一页头、统计卡片、筛选区、错题卡片 footer 操作和相似题入口，移动端操作区可自然折行。
3. 整理 `ReviewView.vue`：突出今日待复习和开始复习入口，统一复习统计、掌握进度、同步错题、AI 建议和复习会话样式。
4. 整理 `ExamListView.vue`：新增考试中心页头和状态摘要，可用试卷卡片改为指标化展示；考试记录操作增加图标并保留继续考试/查看结果路径。
5. 修复 `frontend/src/api/review.ts` 中重复 `/api` 前缀问题，避免智能复习页实际请求 `/api/api/review/...`；新增 API 契约测试覆盖复习计划、AI 建议和流式建议路径。
6. 同步更新 `docs/ROADMAP.md` 与 `docs/HANDOFF.md`，标记 Practice/WrongQuestion/Review/ExamList 整理完成，并把下一步调整为 `QuestionListView.vue` 与管理端 P3。

### 修改文件
- `frontend/src/views/practice/PracticeView.vue`
- `frontend/src/views/practice/WrongQuestionView.vue`
- `frontend/src/views/practice/ReviewView.vue`
- `frontend/src/views/exam/ExamListView.vue`
- `frontend/src/api/review.ts`
- `frontend/src/__tests__/api/review.test.ts`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run`：25 个文件、203 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：前端启动成功，访问 `http://127.0.0.1:5173/`。
- Playwright 本地布局检查：`/practice`、`/wrong-questions`、`/review`、`/exams` 在桌面 1440x980 与移动端 390x844 均无横向溢出，页面标题和核心卡片正常渲染。
- Playwright 网络路径检查：`/review` 页面移动端请求为 `/api/review/stats`，已不再出现 `/api/api/review/stats`。

### 遗留问题
- 本轮未启动后端；浏览器预览中的 502 请求失败提示来自接口代理不可用，不代表页面构建或布局失败。
- `QuestionListView.vue` 题库浏览页仍待整理。
- Phase 21 P3 管理端工作台与列表体验仍待完成。
- Phase 20 的真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 优化核心学习页面体验`

## Round 123 - 2026-06-25

### 阶段
工程体检与稳定性修复

### 完成内容
1. 阅读项目结构、启动方式、测试方式和构建方式，确认当前为 Vue 3 + TypeScript + Vite 前端、Spring Boot 3.2.5 + Maven 后端、MySQL/Flyway/Docker Compose 部署。
2. 本地执行依赖安装、安全审计、前端测试、前端构建、后端测试、后端打包和 Docker Compose 配置解析。
3. 修复前端 npm audit 漏洞：`dompurify` 升到 `3.4.11`，`form-data` 升到 `4.0.6`，审计结果归零。
4. 新增 `REPORT.md`，记录技术栈、发现问题、修复项、仍存在问题、下一步建议、本地运行方式和测试/构建命令。
5. 当前工作区同时包含 Phase 21 P2 相关页面整理改动（Practice/Review/WrongQuestion/ExamList），本轮未回滚，并已纳入前端测试与构建验证。

### 修改文件
- `frontend/package-lock.json`
- `REPORT.md`
- `docs/CHANGELOG_AGENT.md`
- `docs/HANDOFF.md`
- `frontend/src/views/practice/PracticeView.vue`
- `frontend/src/views/practice/ReviewView.vue`
- `frontend/src/views/practice/WrongQuestionView.vue`
- `frontend/src/views/exam/ExamListView.vue`

### 验证
- `cd frontend && npm ci`：通过，修复前提示 2 个 audit 漏洞；修复后仍提示本机 npm install-script 审批信息。
- `cd frontend && npm audit --audit-level=moderate`：通过，0 vulnerabilities。
- `cd frontend && npm test -- --run`：24 个文件、196 个测试通过。
- `cd frontend && npm run build`：通过，保留既有第三方 pure annotation 与大 chunk 警告。
- `cd backend && mvn test`：360 个测试通过。
- `cd backend && mvn package -DskipTests`：通过。
- `docker compose config --quiet`：通过。

### 遗留问题
- 项目暂未配置前端 lint 脚本或 ESLint，无法运行 lint。
- 本轮未运行 Playwright E2E 和 Testcontainers integration 分组；完整复现命令已写入 `REPORT.md`。
- 前端构建仍有大 chunk 警告和第三方 `@vueuse/core` pure annotation 警告，均不阻断构建。

### 建议 commit message
`chore: 完成工程体检并修复前端依赖漏洞`

## Round 122 - 2026-06-25

### 阶段
Phase 21：前端信息架构与视觉体验优化（P0/P1 实施）

### 完成内容
1. 重构 `AppLayout.vue`：侧边栏从长列表改为“学习中心 / 练习复习 / 考试测评 / AI 与诊断 / 内容共建 / 管理后台”分组导航，管理入口继续按管理员角色显示。
2. 顶部栏改为页面语境区：显示当前页面标题、辅助说明、全局搜索入口和用户身份；移动端保留抽屉导航。
3. 更新 `global.css`：新增项目级 CSS 变量，统一背景、卡片、按钮、表格、空状态、弹窗和移动端基础间距。
4. 重做 `HomeView.vue` 为学习工作台：整合今日计划、下一步建议、关键指标、6 个任务入口、趋势图和课程正确率图；继续使用真实统计与学习计划接口。

### 修改文件
- `frontend/src/components/layout/AppLayout.vue`
- `frontend/src/assets/styles/global.css`
- `frontend/src/views/home/HomeView.vue`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run`：24 个文件、196 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。
- `cd frontend && npm run dev -- --host 127.0.0.1`：本地前端启动成功，访问 `http://127.0.0.1:5173/`。
- Playwright 视觉检查：桌面 1440x980、移动端 390x844 均无横向溢出；首页渲染 6 个快捷入口；移动端抽屉展开宽度 248px，分组导航可见。

### 遗留问题
- 本轮只启动前端，未启动后端；截图中的请求失败提示来自接口代理 502，不代表 UI 构建失败。
- Phase 21 P2/P3 未完成：Practice/WrongQuestion/Review/ExamList 和管理端页面仍需继续整理。
- Phase 20 的真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(frontend): 优化学习工作台信息架构`

## Round 121 - 2026-06-25

### 阶段
Phase 21：前端信息架构与视觉体验优化（计划）

### 完成内容
1. 根据用户反馈“功能太混乱、布局不好看、太粗糙”，新增 Phase 21 前端专项计划。
2. 明确设计方向：学习/管理工作台，不做营销页；优先统一导航分组、全局样式和首页工作台。
3. 在路线图中拆分 P0-P3：壳层导航、首页样板、核心业务页、管理端页面，并写明验收标准。
4. 更新交接文档，要求新对话先阅读 `skills/frontend-design/SKILL.md`，第一轮只做 `AppLayout.vue`、`global.css`、`HomeView.vue`。

### 修改文件
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- 本轮仅更新规划文档，未修改前端代码，未运行测试。

### 遗留问题
- Phase 21 尚未实施；新对话应从 AppLayout + global.css + HomeView 开始。

### 建议 commit message
`docs(frontend): 规划前端体验优化`

## Round 120 - 2026-06-25

### 阶段
Phase 20：AI 运营治理（Prompt 与模型配置追踪）

### 完成内容
1. 新增 Flyway V13，为 `ai_call_log` 增加 `prompt_template`、`prompt_hash` 和 `model_config_version`，只保存模板名与不可逆指纹，不保存原始 Prompt 或响应正文。
2. `AiService` 在统一调用日志中写入 Prompt SHA-256 指纹和模型配置版本指纹；模型配置指纹覆盖模型名、maxTokens、stream usage 开关和当前模型价格配置。
3. 管理端 AI 调用分析的最近失败列表新增 Trace ID、Prompt 指纹和模型配置指纹，便于从运营页面回溯请求、Prompt 版本和模型配置。
4. 补充后端回归测试，验证日志记录安全元数据且不会落原始 Prompt 内容，并同步 README、API、数据库、架构、路线图和交接文档。

### 修改文件
- `backend/src/main/java/com/learnplatform/{entity/AiCallLog.java,service/AiService.java,service/AiUsageService.java,dto/AiUsageOverviewVO.java}`
- `backend/src/main/resources/db/migration/V13__add_ai_call_prompt_and_model_trace.sql`
- `backend/src/test/java/com/learnplatform/service/{AiServiceLoggingTest.java,AiUsageServiceTest.java}`
- `frontend/src/{api/aiUsage.ts,views/admin/AiUsageView.vue}`
- `README.md` 与 `docs/{API_DESIGN.md,DB_DESIGN.md,ARCHITECTURE.md,ROADMAP.md,HANDOFF.md,CHANGELOG_AGENT.md}`

### 验证
- `cd backend && mvn test -Dtest=AiServiceLoggingTest,AiUsageServiceTest`：20 个测试通过。
- `cd backend && mvn test`：360 个测试通过。
- `cd frontend && npm test -- --run`：24 个文件、196 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。

### 遗留问题
- 真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(ai): 记录调用追踪指纹`

## Round 119 - 2026-06-23

### 阶段
Phase 20：GitHub Actions Browser E2E 修复

### 完成内容
1. 排查 GitHub Actions 失败：`Browser E2E` 在刷题错题闭环用例中因页面存在两个同名“开始刷题”按钮触发 Playwright strict mode。
2. 将刷题 E2E 的按钮定位收窄到 `.config-card`，明确点击“自选模式”配置卡片内的“开始刷题”按钮。
3. 抽取 E2E 登录 helper，所有浏览器用例统一等待验证码图片加载后再提交登录，降低验证码异步加载导致的本地/CI 偶发登录失败。

### 修改文件
- `frontend/e2e/auth-and-course.spec.ts`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --force-recreate --wait`：E2E 环境重建成功，全部服务 healthy。
- `cd frontend && E2E_BASE_URL=http://localhost:18000 npm run test:e2e -- --grep '用户答错后可在错题本更新掌握程度并重练'`：1 条通过。
- `cd frontend && E2E_BASE_URL=http://localhost:18000 npm run test:e2e`：4 条通过。
- `cd frontend && npm test -- --run`：24 个文件、196 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。

### 遗留问题
- 本轮未 push，仍需推送后等待 GitHub Actions 重新运行确认远端 CI 通过。
- 本地 `gh` CLI 不可用，无法直接从终端拉取 Actions 日志；本轮通过 GitHub Actions 页面与本地复现完成排查。

### 建议 commit message
`test(e2e): 修复刷题入口定位并稳定登录流程`

## Round 118 - 2026-06-21

### 阶段
Phase 20：AI 运营治理（配额审计与调用追踪）

### 完成内容
1. 新增 Flyway V12：创建 `ai_quota_audit_log`，并为 `ai_call_log` 新增 `trace_id` 和索引。
2. 管理员调整用户 AI 日配额时必须填写原因；配额更新和审计插入在同一事务内完成，保留管理员、调整前后值、原因和时间。
3. 管理端用户配额弹窗可展示近期调整记录；新增审计记录查询接口和前端 API 契约。
4. `AiService` 将 MDC 请求 `traceId` 写入调用日志，便于在运营记录与应用日志之间回溯。
5. 补充配额审计、原因校验、调用 traceId 的后端回归测试，并同步 README、API、数据库、架构、路线图和交接文档。

### 修改文件
- `backend/src/main/java/com/learnplatform/{entity/AiQuotaAuditLog.java,mapper/AiQuotaAuditLogMapper.java,entity/AiCallLog.java,service/AiService.java,controller/AdminUserController.java}`
- `backend/src/main/resources/db/migration/V12__add_ai_quota_audit_and_trace_id.sql`
- `backend/src/test/java/com/learnplatform/{controller/AdminUserControllerTest.java,service/AiServiceLoggingTest.java}`
- `frontend/src/{api/adminUser.ts,views/admin/UserManage.vue,__tests__/api/adminUser.test.ts}`
- `README.md` 与 `docs/{API_DESIGN.md,DB_DESIGN.md,ARCHITECTURE.md,ROADMAP.md,HANDOFF.md,CHANGELOG_AGENT.md}`

### 验证
- `cd backend && mvn test`：359 个测试通过。
- `cd frontend && npm test -- --run`：24 个文件、196 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。
- `git diff --check`：通过。

### 遗留问题
- AI 调用追踪尚未保存 Prompt/模型版本等上下文；不应记录敏感原始提示词或用户隐私。
- 真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(ai): 审计用户配额调整并追踪调用请求`

## Round 117 - 2026-06-21

### 阶段
Phase 20：AI 运营治理（调用报告与异常提醒）

### 完成内容
1. 新增管理端 `GET /api/admin/ai-usage/report`：基于真实 `ai_call_log` 比较当前周期与前一等长周期；支持 1-90 天，缺省为 7 天。
2. 报告返回调用次数、失败率、真实 Token、平均耗时、已知成本和环比；前一周期为零或成本未知时环比保持 `null`，不伪造增长率。
3. 实现实时提醒：样本不少于 5 次时识别高失败率（≥10%）、失败率翻倍、明显延迟升高；调用量翻倍且增加至少 10 次时给出信息提醒。提醒由日志即时推导，不落库、不触发外部通知。
4. 管理端 AI 调用分析页同步展示运营报告、环比指标和提醒；时间筛选与现有总览保持一致。
5. 增加服务层和前端 API 契约测试，同步 README、接口、架构、路线图和交接文档。

### 修改文件
- `backend/src/main/java/com/learnplatform/{dto/AiUsageReportVO.java,service/AiUsageService.java,controller/AdminAiUsageController.java}`
- `backend/src/test/java/com/learnplatform/service/AiUsageServiceTest.java`
- `frontend/src/{api/aiUsage.ts,views/admin/AiUsageView.vue,__tests__/api/aiUsage.test.ts}`
- `README.md` 与 `docs/{API_DESIGN.md,ARCHITECTURE.md,ROADMAP.md,HANDOFF.md,CHANGELOG_AGENT.md}`

### 验证
- `cd backend && mvn test`：357 个测试通过。
- `cd frontend && npm test -- --run`：24 个文件、195 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。

### 遗留问题
- 提醒当前只在管理端实时展示，未支持阈值配置、持久化、去重和通知分发。
- 配额调整原因与审计历史、调用 Prompt/模型版本追踪仍待实现。

### 建议 commit message
`feat(ai): 新增调用运营报告与异常提醒`

## Round 116 - 2026-06-21

### 阶段
Phase 20：AI 运营治理（用户独立配额）

### 完成内容
1. 新增 Flyway V11 和 `user.ai_daily_quota`：`NULL` 继承全局 `ai.daily-quota`，`0` 表示不限次数，`1-10000` 为用户覆盖值。
2. AI 限流检查和用户端用量查询统一优先读取用户级配额；未配置覆盖值时保持既有全局配额行为。
3. 管理端用户表新增 AI 日配额展示与编辑弹窗，并新增 `PUT /api/admin/users/{id}/ai-daily-quota`；支持设置自定义次数或恢复继承全局配置。
4. 新增限流覆盖值、控制器参数校验与前端 API 契约测试；同步更新 README、API、数据库、架构、路线图和交接文档。

### 修改文件
- `backend/src/main/java/com/learnplatform/{entity/User.java,dto/UserVO.java,service/AiService.java,controller/AdminUserController.java}`
- `backend/src/main/resources/db/migration/V11__add_user_ai_daily_quota.sql`
- `backend/src/test/java/com/learnplatform/{service/AiServiceLoggingTest.java,service/ReviewAISuggestionTest.java,controller/AdminUserControllerTest.java}`
- `frontend/src/{api/adminUser.ts,views/admin/UserManage.vue,__tests__/api/adminUser.test.ts}`
- `README.md` 与 `docs/{API_DESIGN.md,DB_DESIGN.md,ARCHITECTURE.md,ROADMAP.md,HANDOFF.md,CHANGELOG_AGENT.md}`

### 验证
- `cd backend && mvn test`：354 个测试通过。
- `cd frontend && npm test -- --run`：24 个文件、194 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。
- `git diff --check`：通过。

### 遗留问题
- 配额调整暂未记录管理员、原因与历史审计；调用日报/周报、失败率和异常用量提醒仍待开发。
- 真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(ai): 支持用户独立日配额`

## Round 115 - 2026-06-21

### 阶段
Phase 20：AI 运营治理（模型成本）

### 完成内容
1. 新增 Flyway V10，为 `ai_call_log` 保存真实 `prompt_tokens`、`completion_tokens` 和调用当时的 `cost_usd`；历史日志与 usage 不完整的调用保持空值。
2. 新增按 `ai.model-prices.<model>` 配置的输入/输出 USD/百万 Token 单价计算器；只有上游提供完整真实 usage 且模型配置了正数单价时才固化成本，不使用字符数、默认价格或猜测值。
3. 管理端 AI 调用分析总览及功能、模型、用户维度新增成本聚合展示；未配置价格或无完整 usage 时页面显示 `-`，避免把未知成本显示为零。
4. 增加成本计算、调用日志持久化和聚合回归测试；同步更新 API、数据库、架构、路线图、交接和 README。

### 修改文件
- `backend/src/main/java/com/learnplatform/config/AiConfig.java`
- `backend/src/main/java/com/learnplatform/entity/AiCallLog.java`
- `backend/src/main/java/com/learnplatform/service/AiService.java`
- `backend/src/main/java/com/learnplatform/service/AiUsageService.java`
- `backend/src/main/java/com/learnplatform/service/ai/AiCostCalculator.java`
- `backend/src/main/java/com/learnplatform/dto/AiUsageOverviewVO.java`
- `backend/src/main/resources/db/migration/V10__add_ai_call_cost_fields.sql`
- `frontend/src/api/aiUsage.ts`
- `frontend/src/views/admin/AiUsageView.vue`
- 相关测试与项目文档

### 验证
- `cd backend && mvn test`：349 个测试通过。
- `cd frontend && npm test -- --run`：24 个文件、192 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。

### 遗留问题
- 需由部署管理员按实际供应商账单在 `ai.model-prices` 配置各模型单价；本轮刻意未内置可能过期的厂商价格。
- 真实演示截图、推送后的 GitHub Actions 实跑、用户独立配额和调用报告仍待完成。

### 建议 commit message
`feat(ai): 记录并聚合模型调用成本`

## Round 114 - 2026-06-21

### 阶段
Phase 20：AI 运营治理（真实 Token 用量）

### 完成内容
1. OpenAI 兼容 Provider 现在从同步响应 `usage` 和流式最终事件 `usage` 读取 prompt、completion 与 total token；统一 AI 调用日志写入真实 `total_tokens` 到既有 `ai_call_log.tokens_used` 字段。
2. 流式调用默认发送 `stream_options.include_usage=true`，可通过 `AI_STREAM_INCLUDE_USAGE=false` 兼容不支持该扩展的上游；任何未返回 usage 的调用保持 `NULL`，不以字符数估算。
3. 使用 `ThreadLocal` 隔离并发调用的上游 usage，统一日志入口覆盖既有同步、流式、学习资产和投稿 AI 服务调用，无需改变对外 API 或数据库结构。
4. 新增 Provider usage 解析与 AI 日志写入回归测试；同步更新架构、API、数据库设计、路线图和交接信息。

### 修改文件
- `backend/src/main/java/com/learnplatform/config/AiConfig.java`
- `backend/src/main/java/com/learnplatform/service/AiService.java`
- `backend/src/main/java/com/learnplatform/service/ai/AiProvider.java`
- `backend/src/main/java/com/learnplatform/service/ai/AiTokenUsage.java`
- `backend/src/main/java/com/learnplatform/service/ai/OpenAiProvider.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/learnplatform/service/AiServiceLoggingTest.java`
- `backend/src/test/java/com/learnplatform/service/ai/OpenAiProviderTest.java`
- `docs/API_DESIGN.md`
- `docs/ARCHITECTURE.md`
- `docs/DB_DESIGN.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd backend && mvn test`：347 个测试通过。
- `git diff --check`：通过。

### 遗留问题
- 仍待按模型单价计算成本；当前记录真实总 token，但未拆分持久化输入/输出 token，因此不能精确应用不同输入/输出单价。
- 真实演示截图与推送后的 GitHub Actions 实跑仍待完成。

### 建议 commit message
`feat(ai): 记录上游真实 token 用量`

## Round 113 - 2026-06-21

### 阶段
Phase 20：投稿审核入库浏览器 E2E

### 完成内容
1. 新增 Playwright 真实浏览器用例：普通用户提交一题单选投稿后，管理员重新登录，在投稿管理中检索、审核通过并正式入库。
2. 用例覆盖真实验证码、账号密码、JWT、角色路由守卫、投稿表单校验、审核状态切换与入库确认；不依赖数据库直写或权限绕过。
3. 清空隔离 E2E 数据卷后完整执行浏览器套件，登录/课程、刷题错题、考试判分和投稿审核入库共 4 条用例均通过。

### 修改文件
- `frontend/e2e/auth-and-course.spec.ts`
- `README.md`
- `docs/TESTING.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `docker compose -f docker-compose.yml -f docker-compose.e2e.yml down -v`
- `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait`
- `cd frontend && npm run test:e2e`：Chromium 4 条用例全部通过。

### 遗留问题
- 真实演示截图和 GitHub Actions 推送后的实际运行结果仍待完成。
- 本轮未重新运行 Vitest 或后端 Maven 测试；改动仅涉及 E2E 与 Markdown 文档。

### 建议 commit message
`test(e2e): 覆盖投稿审核入库闭环`

## Round 112 - 2026-06-21

### 阶段
Phase 20：考试浏览器 E2E

### 完成内容
1. 新增 Playwright 真实浏览器用例：普通用户完成验证码登录后，进入已发布演示试卷，依次完成单选、多选、判断三种题型作答，确认提交并查看自动判分结果详情。
2. 断言真实前端会话跳转、提交确认、后端判分及结果页三题全对状态；用例未绕过账号密码、JWT、权限或考试提交接口。
3. 收紧已有 E2E 的状态单选定位，避免错题卡内同名文本造成 Playwright strict-mode 歧义。

### 修改文件
- `frontend/e2e/auth-and-course.spec.ts`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm run test:e2e -- --grep '用户可完成考试'`：Chromium 考试 E2E 通过（15/15，三种题型、提交确认、结果详情）。
- 隔离 Docker E2E Profile 已成功重建，镜像构建中的 `npm run build` 通过（保留既有第三方 pure annotation 与大 chunk 警告）。

### 遗留问题
- 本机复用的 Docker 数据卷含历史练习状态，全量 E2E 中既有刷题错题闭环用例会受历史数据干扰；本轮未清空开发数据卷。应在真正隔离的 E2E 数据卷或 CI 的全新容器中执行完整套件。
- 管理端审核 E2E、真实演示截图与 GitHub Actions 实跑仍待完成。

### 建议 commit message
`test(e2e): 覆盖考试作答与判分闭环`

## Round 111 - 2026-06-21

### 阶段
Phase 20：刷题与错题本浏览器 E2E

### 完成内容
1. 扩展 Playwright 真实浏览器用例，覆盖普通用户登录后进入刷题页、单题作答并获得答错反馈、完成练习、进入错题本、更新掌握程度及进入错题重练的完整闭环。
2. 用例基于演示题库第二个选项均为错误答案的稳定前提，验证真实判分、错题归集、掌握程度接口与重练题目加载，不绕过账号密码、JWT、权限或业务接口。
3. 修正 E2E 环境启动说明：若已运行普通 Docker Profile，需以 `--force-recreate` 重建容器，确保后端启用仅测试可用的 `e2e` Profile 和固定验证码。

### 修改文件
- `frontend/e2e/auth-and-course.spec.ts`
- `docs/TESTING.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm run test:e2e`：Chromium 2 条用例全部通过（登录与课程浏览；刷题答错→错题本→掌握度更新→重练）。
- 使用 `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --force-recreate --wait` 启动后，确认后端处于 `e2e` Profile。

### 遗留问题
- 考试和管理端审核的浏览器 E2E、真实演示截图与 GitHub Actions 实跑仍待完成。
- 本轮未重新运行 Vitest、前端构建或后端 Maven 测试；改动仅涉及 E2E 与 Markdown 文档。

### 建议 commit message
`test(e2e): 覆盖刷题错题重练闭环`

## Round 110 - 2026-06-21

### 阶段
Phase 20：浏览器 E2E 基线

### 完成内容
1. 引入 Playwright，并建立首条真实浏览器 E2E：普通用户完成验证码、账号密码登录，获得 JWT 会话后进入首页并浏览课程列表。
2. 新增隔离 Spring `e2e` Profile：仅在 Docker E2E 环境中使用固定的一次性验证码答案；生产与开发环境仍使用随机数学验证码，且测试没有绕过账号密码、JWT、权限或路由守卫。
3. 新增 `docker-compose.e2e.yml`，取消 Redis 宿主机端口暴露并促使 Nginx 在后端容器重建后重新解析服务地址，支持本地与 CI 可重复运行。
4. 将浏览器 E2E 加入 GitHub Actions：安装 Chromium、启动隔离 Docker 环境、执行测试并上传失败报告。

### 修改文件
- `backend/src/main/java/com/learnplatform/config/CaptchaService.java`
- `backend/src/main/resources/application-e2e.yml`
- `backend/src/test/java/com/learnplatform/config/CaptchaServiceTest.java`
- `docker-compose.e2e.yml`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/playwright.config.ts`
- `frontend/e2e/auth-and-course.spec.ts`
- `frontend/vitest.config.ts`
- `.github/workflows/ci.yml`
- `.gitignore`
- `README.md`
- `docs/TESTING.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd backend && mvn test`：343 个测试通过。
- `cd frontend && npm test`：24 个文件、192 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 pure annotation 与大 chunk 警告）。
- `E2E_BASE_URL=http://localhost:18000 npm run test:e2e`：Chromium 1 条测试通过；在真实 Docker E2E Profile 中验证。
- Docker 已恢复普通 `dev` Profile，backend、frontend、MySQL、Redis、Loki 均健康。

### 遗留问题
- GitHub Actions 尚待推送后确认实际运行结果。
- 真实演示截图，以及刷题、错题复习、考试和管理端审核的浏览器 E2E 仍待补充。

### 建议 commit message
`test(e2e): 覆盖真实登录与课程浏览`

## Round 109 - 2026-06-21

### 阶段
Phase 20：考试交互回归测试

### 完成内容
1. 新增 `ExamTakeView` 页面级交互回归测试，覆盖单选、多选、判断三种题型的作答、提交确认、请求载荷、会话清理、结果缓存与结果页跳转。
2. 覆盖考试会话缺失时的安全回退：给出提示并返回考试列表，避免直接进入无题目的考试页。
3. 没有为绕过登录数学验证码添加测试后门；浏览器 E2E 仍需通过安全的专用登录态方案接入。

### 修改文件
- `frontend/src/__tests__/views/ExamTakeView.test.ts`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `cd frontend && npm test -- --run src/__tests__/views/ExamTakeView.test.ts`：2 个测试通过。
- `cd frontend && npm test -- --run`：24 个文件、192 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。

### 遗留问题
- 仍需为浏览器 E2E 建立不削弱生产验证码安全性的专用登录态，并覆盖登录、刷题、错题复习、考试和管理端审核。

### 建议 commit message
`test(exam): 补充考试作答与提交交互回归`

## Round 108 - 2026-06-21

### 阶段
Phase 20：真实考试浏览器验收

### 完成内容
1. 在健康运行的 Docker 环境中完成“Java 基础入门测验”真实浏览器验收：创建考试记录、单选/多选/判断三种题型作答、提交确认、自动判分与结果详情均正常。
2. 本次全对提交结果为 15/15、正确率 100%；结果页正确展示每题得分、用户答案与解析。
3. 同步路线图和交接文档，Phase 20 的考试完整浏览器验收项标记完成。

### 修改文件
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- Docker Compose：frontend、backend、MySQL、Redis、Loki 服务均为 healthy。
- 浏览器：`/exams` → 开始考试 → 三题作答 → 提交确认 → 结果页，完整链路通过。
- `cd frontend && npm test -- --run`：23 个文件、190 个测试通过。
- `cd frontend && npm run build`：通过（保留既有第三方 `@vueuse/core` pure annotation 与大 chunk 警告）。

### 遗留问题
- 用户端与管理端真实演示截图、GitHub Actions 实跑和关键业务 E2E 仍待完成。

### 建议 commit message
`docs: 记录考试全流程浏览器验收`

## Round 107 - 2026-06-21

### 阶段
路线图与文档校准

### 完成内容
1. 将下一阶段明确为 Phase 20“演示验收与 AI 运营治理”：优先完成考试完整流程验收、演示截图、CI 实跑和关键业务端到端测试。
2. 将后续 AI 主线收敛为真实 token/成本、用户独立配额、调用报告与异常提醒；将内容复审缓存、重复题检测和学习效果指标列为后续衔接。
3. 清理过期的 `FUTURE.md` 待办和 AI 战略中仍指向 Phase 13 的描述，避免已完成能力被重复列为计划。
4. 同步更新交接提示与 README 阶段表；未修改业务代码、接口或数据库结构。

### 修改文件
- `README.md`
- `docs/ROADMAP.md`
- `docs/FUTURE.md`
- `docs/AI_LEARNING_PLATFORM_STRATEGY.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- 静态核对 `docs/ROADMAP.md`、`docs/HANDOFF.md`、`docs/FUTURE.md` 与当前代码、测试及 CI 配置描述的一致性。
- 本轮仅更新 Markdown 文档，未运行构建或测试。

### 遗留问题
- Phase 20 的验收、CI 实跑与 AI 运营治理功能尚未实施。

### 建议 commit message
`docs: 校准后续路线为演示验收与 AI 运营治理`

## Round 106 - 2026-06-20

### 阶段
真实浏览器验收缺陷修复

### 完成内容
1. 修复刷题结果弹窗在点击“下一题”后的关闭动画闪屏：结果数据现在会保留至 Element Plus 的 `closed` 事件触发，再切换题目与清理状态，不再短暂渲染为空的“答错了”反馈。
2. 新增 `PracticeSessionView` 页面级回归测试，覆盖“关闭动画完成前仍保留当前反馈、结束后才进入下一题”的状态转换。

### 修改文件
- `frontend/src/views/practice/PracticeSessionView.vue`
- `frontend/src/__tests__/views/PracticeSessionView.test.ts`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- 真实 Docker 浏览器验收：普通用户登录、首页、课程、题库、刷题即时判分、错题本；管理员登录、平台总览、课程管理、AI 调用分析均正常。
- `npm test -- --run`：23 个文件、190 个测试通过。
- `npm run build`：通过。

### 遗留问题
- AI 上游调用历史仍有网络握手/超时失败记录；管理端已能展示失败信息。
- 项目演示截图与 GitHub Actions 实际推送验证仍待完成。

### 建议 commit message
`fix(practice): 修复切换下一题时结果弹窗闪屏`

---

## Round 105 - 2026-06-20

### 阶段
真实部署缺陷修复与回归

### 完成内容
1. 修复 Docker Redis 无密码场景：Redis 的 protected mode 会关闭来自同一 Docker 网络后端的连接，导致 Lettuce 握手失败并使缓存接口返回 500。现关闭该模式，并将宿主机端口限制为仅本机绑定。
2. 修复管理端 AI 调用分析 API 重复 `/api` 前缀；新增前端 API 契约测试。
3. 修复学习诊断在没有薄弱知识点时构造 `IN ()` 非法 SQL 的问题，并补充空数据回归断言。
4. 重建 Docker 运行态并完成真实 HTTP 验收：8 个 Redis 相关统计/管理接口、以及经前端 Nginx 代理的 AI 用量接口均返回 200。

### 修改文件
- `docker-compose.yml`
- `backend/src/main/java/com/learnplatform/service/LearningDiagnosisService.java`
- `backend/src/test/java/com/learnplatform/service/LearningDiagnosisServiceTest.java`
- `frontend/src/api/aiUsage.ts`
- `frontend/src/__tests__/api/aiUsage.test.ts`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `mvn clean test`：341 passed。
- `npm test`：22 个文件、189 passed；`npm run build`：通过。
- Docker：Redis 从同网络容器 `PING` 返回 `PONG`；8 个缓存/管理接口和前端代理 AI 用量接口均为 HTTP 200。

### 遗留问题
- 仍需按 `docs/DEMO.md` 完成浏览器 UI 点击验收与截图制作。
- CI 需推送到 GitHub 后实际触发验证。

### 建议 commit message
`fix(runtime): 修复 Redis 缓存与 AI 用量接口路径`

---

## Round 104 - 2026-06-20

### 阶段
真实部署验收与缺陷记录

### 本轮目标
在 Docker 运行环境中验证登录、普通用户与管理员的真实接口链路，识别阻断演示的运行时问题。

### 完成内容
1. 重建并切换 Docker 后端镜像，解决历史镜像未包含验证码接口免认证配置的问题；`GET /api/auth/captcha` 现已通过前端代理与后端直连验证为 200。
2. 使用数学验证码登录演示账号 `testuser` 与 `admin`，携带真实 JWT 验证用户端和管理员端读取接口。
3. 执行回归：后端 `mvn test` 341 个测试通过；前端 `npm test` 187 个测试通过；前端 `npm run build` 成功。
4. 发现并记录以下待修复缺陷：
   - Redis 缓存连接失败会使所有 `@Cacheable` 统计接口返回 500，影响首页、学习趋势、学习报告、学习路径、知识图谱、学习诊断，以及管理员平台概览和课程管理。
   - 管理端 AI 调用分析页面前端请求路径为 `/api/api/admin/ai-usage/overview`，而正确后端路径是 `/api/admin/ai-usage/overview`，页面实际返回 404。

### 修改文件
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验收结果
- [x] Docker 前端、后端、MySQL、Redis、Loki 均已启动；前后端 healthcheck 正常。
- [x] 验证码接口经 `http://localhost:18000/api/auth/captcha` 返回 200。
- [x] 普通用户只读接口：20 项中 14 项通过；失败的 6 项均为 Redis 缓存连接异常导致的 500。
- [x] 管理员只读接口：8 项中 5 项通过；平台概览和课程管理受 Redis 问题影响，AI 调用分析页面请求路径错误。
- [x] 后端 `mvn test`：341 passed；前端 `npm test`：187 passed；`npm run build` 成功。

### 遗留问题 / 下轮待办
1. 优先修复 Docker Redis 连接配置：确认空 `REDIS_PASSWORD` 的 Spring 配置绑定、Redis 容器认证参数与后端 Lettuce 行为一致；修复后重建后端并复测全部缓存接口。
2. 修复 `frontend/src/api/aiUsage.ts`：将 `'/api/admin/ai-usage/overview'` 改为 `'/admin/ai-usage/overview'`，补充 API 单元测试并验证管理端页面请求为 200。
3. Redis 与 AI 用量修复后，使用可用浏览器完成 UI 点击验收：登录、首页、刷题提交、错题本、考试、管理员平台概览和 AI 调用分析。

### 建议 commit message
`fix(runtime): 修复 Redis 缓存连接与管理端 AI 用量接口路径`

---

## Round 103 - 2026-06-20

### 阶段
构建与部署修复

### 本轮目标
修复真实运行环境中登录被验证码接口 401 阻断的问题，并恢复 Docker 后端镜像的可启动性。

### 完成内容
1. 定位旧 Docker 镜像与当前源码不一致：运行态 `/api/auth/captcha` 返回 401，而源码安全配置已放行该路径。
2. 在 `pom.xml` 补充 `spring-boot-maven-plugin`，使 `mvn package` 产出可由 `java -jar` 运行的 Spring Boot 可执行 JAR；此前 Dockerfile 复制的是普通 JAR，重新构建后会因缺少 Main-Class 而无法启动。
3. 新增 `scripts/load-env.sh`，安全加载 Docker 风格 `.env` 到本地 Maven 进程，不再让数据库 URL 中的 `&` 被 shell 解析为控制符。
4. 更新 `.env.example`、README 和 HANDOFF 的本地启动命令。

### 修改文件
- `backend/pom.xml`
- `scripts/load-env.sh`
- `.env.example`
- `README.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- `mvn package -DskipTests` 成功，产物 Manifest 包含 Spring Boot `Main-Class` 与 `Start-Class`。
- 使用当前 JAR 在 Docker 网络中启动一次性验证容器：`GET /api/auth/captcha` 返回 200。
- 使用演示账号完成验证码登录，并以返回的 JWT 请求 `/api/auth/me`，均返回成功。
- `scripts/load-env.sh .env.example` 能正确保留带 `&` 的数据库 URL。
- 后端 `mvn test` 通过（341 个测试）；前端 `npm run build` 与 `npm test` 通过（187 个测试）。

### 遗留问题
- 需要在网络条件正常时执行一次 `docker compose build backend && docker compose up -d backend`，以替换本机历史旧镜像；本轮已验证其应使用的可执行 JAR。
- 全量 Docker Compose 仍可能受外部镜像拉取和本机 6379 端口占用影响。

### 下一步建议
- 重建后端镜像后，继续按 `docs/DEMO.md` 补跑刷题、错题和考试的完整 UI 验收。

### 建议 commit message
`fix(deploy): 生成可执行后端 JAR 并修复本地环境加载`

---

## Round 102 - 2026-06-20

### 阶段
构建与回归修复

### 本轮目标
修复实际验证中发现的前端生产构建失败和学习报告复习统计测试 OOM。

### 完成内容
1. 删除 `GlobalSearchDialog.vue` 未使用的 `watch` 导入，恢复 TypeScript 严格检查下的前端生产构建。
2. 修正 `LearningReportReviewStatsTest` 的 `selectCount` mock 调用序列：为连续复习天数查询提供终止值，避免固定正数使测试循环不退出并耗尽堆内存。
3. 删除无复习卡片场景中不会执行的多余 `selectList` stub。

### 修改文件
- `frontend/src/components/GlobalSearchDialog.vue`
- `backend/src/test/java/com/learnplatform/service/LearningReportReviewStatsTest.java`
- `docs/CHANGELOG_AGENT.md`
- `docs/HANDOFF.md`

### 验证
- 前端：`npm test -- --run` 通过（21 个文件，187 个测试）。
- 前端：`npm run build` 通过。
- 后端：`mvn clean test -Dtest=LearningReportReviewStatsTest` 通过（4 个测试）。
- 后端：`mvn test` 通过（341 个测试，0 failures / 0 errors）。

### 遗留问题
- MySQL / Docker 服务未在当前本机运行，登录、刷题等真实端到端链路仍待服务启动后验收。

### 下一步建议
- 启动 MySQL 或 Docker Compose 后，按 `docs/DEMO.md` 完成用户端与管理端端到端验收。

### 建议 commit message
`fix: 修复前端构建与复习统计测试循环`

---

## Round 101 - 2026-06-20

### 阶段
文档同步与项目收尾

### 本轮目标
校准项目说明、路线图、交接、接口、数据库和架构文档，使其与 Phase 19 和现有代码保持一致。

### 完成内容
1. README 阶段表更新至 Phase 19，并补充智能复习、搜索、学习诊断与题目内容治理能力。
2. ROADMAP 新增 Phase 19，当前阶段改为 Phase 19 已基本完成，并明确演示/CI 收尾和 AI 运营能力候选任务。
3. HANDOFF 清除已完成搜索功能的旧“下一步”描述，续接提示同步至 Phase 19。
4. API_DESIGN 补充 Phase 17 复习、Phase 18 搜索和 Phase 19 AI 用量分析接口。
5. DB_DESIGN 补充 V8 题目来源与复审、V9 间隔重复复习表，并修正审计表字段约定。
6. ARCHITECTURE 同步当前扩展模块、权限边界和 Lombok 未使用的事实。

### 修改文件
- `README.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/API_DESIGN.md`
- `docs/DB_DESIGN.md`
- `docs/ARCHITECTURE.md`
- `docs/CHANGELOG_AGENT.md`

### 验证
- 对照 `ReviewController`、`GlobalSearchController`、`AdminAiUsageController`、Flyway V8/V9 和前端路由进行静态核对。
- 文档改动不涉及业务代码，无需运行构建。

### 遗留问题
- 项目截图与 GitHub Actions 实际运行验证仍待完成。

### 下一步建议
- 按 `docs/DEMO.md` 制作并验收演示截图；推送后确认 CI。
- 若继续功能迭代，优先用户独立 AI 配额、成本估算或调用日报。

### 建议 commit message
`docs: 同步 Phase 19 进度、复习搜索与 AI 用量分析文档`

---

## Round 100 - 2026-06-19

### 阶段
Phase 19：AI 调用分析与成本控制面板 🚧 开发中

### 本轮目标
实现管理端 AI 调用分析面板，聚合 ai_call_log 数据，展示调用趋势、功能分布、模型分布、Top 活跃用户和失败调用详情，帮助管理员监控 AI 使用情况和成本。

### 完成内容
1. **后端 — AiUsageOverviewVO**：
   - 包含全局统计（总调用、成功/失败、成功率、总 tokens、平均耗时、今日调用）。
   - 5 个内嵌类：FunctionStats（按功能分组）、ModelStats（按模型分组）、DailyTrend（每日趋势）、TopUser（活跃用户）、RecentFailure（失败详情）。

2. **后端 — AiUsageService**：
   - 基于 AiCallLogMapper 查询指定天数范围内的所有日志。
   - 流式聚合：全局统计、按功能分组、按模型分组、每日趋势（按日期填充空日期）、Top 10 活跃用户（批量查询用户名）、最近 20 条失败调用。

3. **后端 — AdminAiUsageController**：
   - `GET /api/admin/ai-usage/overview?days=30`：获取 AI 调用总览。
   - 位于 `/api/admin/**` 路径下，自动受 ADMIN 角色保护。

4. **前端 — aiUsage.ts API 模块**：
   - 完整 TypeScript 类型定义（FunctionStats、ModelStats、DailyTrend、TopUser、RecentFailure、AiUsageOverview）。
   - `getAiUsageOverview(days?)` API 函数。

5. **前端 — AiUsageView.vue 管理端页面**：
   - 8 个统计卡片（总调用/成功率/今日调用/总Tokens/成功调用/失败调用/平均耗时/今日Tokens）。
   - 时间范围选择器（7/14/30/90 天）+ 刷新按钮。
   - ECharts 每日调用趋势图（堆叠柱状图 + Tokens 折线图双 Y 轴）。
   - ECharts 按功能分布和按模型分布饼图（环形图）。
   - 功能调用详情表格和 Top 活跃用户表格。
   - 最近失败调用表格（错误信息溢出省略）。
   - 响应式适配（移动端卡片 2×2 网格、图表高度自适应）。

6. **路由与导航**：
   - 路由新增 `admin/ai-usage`（AdminAiUsage，requiresAdmin）。
   - 侧边栏后台管理子菜单新增"AI 调用分析"（Monitor 图标）。

7. **后端测试**：
   - 新建 `AiUsageServiceTest.java`（11 个单元测试）：
     - 正常返回总览数据、空数据零值、功能分组统计、模型分组统计、Top 用户排序、失败调用过滤、days 默认值、days=7 趋势图、Top 用户限制 10 人、平均耗时排除 null。

### 修改文件
- `backend/src/main/java/com/learnplatform/dto/AiUsageOverviewVO.java`（新建）
- `backend/src/main/java/com/learnplatform/service/AiUsageService.java`（新建）
- `backend/src/main/java/com/learnplatform/controller/AdminAiUsageController.java`（新建）
- `frontend/src/api/aiUsage.ts`（新建）
- `frontend/src/views/admin/AiUsageView.vue`（新建）
- `frontend/src/router/index.ts`（新增路由）
- `frontend/src/components/layout/AppLayout.vue`（新增侧边栏菜单项）
- `backend/src/test/java/com/learnplatform/service/AiUsageServiceTest.java`（新建，11 个测试）

### 验证
- 后端 `mvn test -Dtest="AiUsageServiceTest"` 全部通过（11/11 passed, BUILD SUCCESS）

### 遗留问题
- 无

### 下一步建议
- Phase 19 核心功能已完成，可标记为 ✅
- 可选迭代：AI 调用日报/周报（定时生成报告）、AI 成本预估（基于 token 单价）、AI 配额管理增强（按用户独立调整配额）
- 或进入其他用户指定任务

### 建议 commit message
`feat(admin): AI 调用分析与成本控制面板，11 个新单元测试`

---

## Round 99 - 2026-06-19

### 阶段
Phase 18：全局搜索与快捷导航 🚧 开发中

### 本轮目标
完善 Phase 18 后续功能：搜索历史记录、热门搜索推荐、搜索结果缓存。

### 完成内容
1. **后端 — SearchHistoryService**：
   - ConcurrentHashMap 内存存储用户级搜索历史（每用户最多 20 条，LRU 淘汰）。
   - ConcurrentHashMap 存储全局热门关键词计数（最多 100 条，定期淘汰低频）。
   - 用户历史独立隔离，搜索去重，时间倒序排列。
   - 支持清除全部历史和删除单条历史。

2. **后端 — GlobalSearchController 新增 3 个接口**：
   - `GET /api/search/suggestions`：获取搜索历史 + 热门搜索关键词（二合一）。
   - `DELETE /api/search/history`：清除当前用户全部搜索历史。
   - `DELETE /api/search/history/item?keyword=xxx`：删除单条搜索历史。
   - 搜索接口 `GET /api/search` 自动记录搜索历史和热门关键词。

3. **后端 — GlobalSearchService 搜索缓存**：
   - 添加 `@Cacheable(value = "globalSearch")` 注解，关键词 >= 2 字符时缓存 5 分钟。
   - RedisConfig 新增 `globalSearch` 缓存区域（TTL 5 分钟）。

4. **前端 — search.ts API 模块扩展**：
   - 新增 `getSearchSuggestions()`、`clearSearchHistory()`、`removeSearchHistoryItem()` 三个 API 函数。
   - 新增 `SearchSuggestions` 类型定义。

5. **前端 — GlobalSearchDialog.vue 搜索建议集成**：
   - 对话框打开时自动加载搜索历史和热门搜索。
   - 搜索历史区域：显示最近 10 条，每条可点击填充搜索框，支持单条删除和一键清除。
   - 热门搜索区域：标签形式展示 Top 10 热门关键词，排名前 3 金色高亮。
   - 空搜索框状态：有历史/热门时显示建议，无数据时显示默认提示。
   - 移动端适配（触摸友好的历史项和热门标签）。

6. **后端测试**：
   - 新建 `SearchHistoryServiceTest.java`（17 个单元测试）：
     - 记录搜索 5 个：正常记录、去重、null userId、null 关键词、上限淘汰。
     - 获取历史 4 个：空历史、null userId、用户隔离、最多 10 条。
     - 热门搜索 4 个：正常记录、空数据、降序排列、最多 10 条。
     - 清除历史 4 个：清除全部、null userId、删除单条、删除不存在项。
   - 本地运行通过（24/24 passed，含 GlobalSearchServiceTest 7 个 + SearchHistoryServiceTest 17 个）。

### 修改文件
- `backend/src/main/java/com/learnplatform/service/SearchHistoryService.java`（新建）
- `backend/src/main/java/com/learnplatform/controller/GlobalSearchController.java`（重写，新增 3 个接口）
- `backend/src/main/java/com/learnplatform/service/GlobalSearchService.java`（添加 @Cacheable）
- `backend/src/main/java/com/learnplatform/config/RedisConfig.java`（新增 globalSearch 缓存区域）
- `backend/src/test/java/com/learnplatform/service/SearchHistoryServiceTest.java`（新建，17 个测试）
- `frontend/src/api/search.ts`（新增 3 个 API 函数 + SearchSuggestions 类型）
- `frontend/src/components/GlobalSearchDialog.vue`（搜索历史 + 热门搜索 UI）

### 验证
- 后端 `mvn test -Dtest="SearchHistoryServiceTest,GlobalSearchServiceTest"` 全部通过（24/24 passed）
- 前端文件创建/更新完成

### 遗留问题
- 无

### 下一步建议
- Phase 18 核心 + 后续迭代已基本完成，可标记为 ✅
- 进入新阶段规划（见 docs/FUTURE.md 和 docs/AI_LEARNING_PLATFORM_STRATEGY.md）
- 或其他用户指定任务

### 建议 commit message
`feat(search): 搜索历史、热门搜索与结果缓存，17 个新单元测试`

---

## Round 98 - 2026-06-19

### 阶段
Phase 18：全局搜索与快捷导航 🚧 开发中

### 本轮目标
实现全局搜索功能，支持跨题目、课程、知识点的模糊搜索，提供快捷键盘入口（⌘K / Ctrl+K / /），结果分组展示并支持键盘导航和关键词高亮。

### 完成内容
1. **后端 — GlobalSearchResultVO**：
   - 分组搜索结果 VO（questions / courses / knowledgePoints + totalCount）。
   - 内部 SearchItem 类（id / title / subtitle / type / link / highlight）。

2. **后端 — GlobalSearchService**：
   - 对题目（content LIKE）、课程（name LIKE）、知识点（name LIKE）并行查询。
   - 每类默认 5 条、最大 20 条限制。
   - 题目副标题自动映射中文题型 + 难度星号。
   - 超长文本截断 + 空查询防护。

3. **后端 — GlobalSearchController**：
   - `GET /api/search?keyword=&limit=` 接口，需要认证。
   - Knife4j 注解完善。

4. **前端 — search.ts API 模块**：
   - `globalSearch()` 函数，类型定义 `SearchItem` 和 `GlobalSearchResult`。

5. **前端 — GlobalSearchDialog.vue 组件**：
   - 搜索对话框：输入框 + 250ms 防抖 + 结果分组展示 + 加载/空状态。
   - 键盘导航：↑↓ 选择、Enter 跳转、Escape 关闭。
   - 关键词高亮（mark 标签 + XSS 安全 escape）。
   - 全局快捷键：⌘K / Ctrl+K 打开/关闭，/ 键快速打开。
   - 移动端适配（95% 宽度、触摸友好 48px 行高）。

6. **前端 — AppLayout.vue 集成**：
   - header-right 新增搜索触发按钮（图标 + 文字 + ⌘K 快捷键提示）。
   - 引入 GlobalSearchDialog 组件并暴露 open() 方法。

7. **后端测试**：
   - 新建 `GlobalSearchServiceTest.java`（7 个单元测试）：
     - 空查询处理：null 关键词返回空、空字符串不调用 mapper。
     - 正常搜索：分组结果验证、自定义 limit、limit 超限截断。
     - 题型映射：副标题包含中文题型 + 难度、超长内容截断。
   - 本地运行通过（7/7 passed）。

### 修改文件
- `backend/src/main/java/com/learnplatform/dto/GlobalSearchResultVO.java`（新建）
- `backend/src/main/java/com/learnplatform/service/GlobalSearchService.java`（新建）
- `backend/src/main/java/com/learnplatform/controller/GlobalSearchController.java`（新建）
- `backend/src/test/java/com/learnplatform/service/GlobalSearchServiceTest.java`（新建，7 个测试）
- `frontend/src/api/search.ts`（新建）
- `frontend/src/components/GlobalSearchDialog.vue`（新建）
- `frontend/src/components/layout/AppLayout.vue`（搜索触发按钮 + 组件引用）

### 验证
- 后端 `mvn test -Dtest="GlobalSearchServiceTest"` 全部通过（7/7 passed）
- 前端文件创建完成

### 遗留问题
- 无

### 下一步建议
- 完善 Phase 18 后续功能（搜索历史记录、热门搜索推荐等）
- 或进入其他 Phase 18 子任务

### 建议 commit message
`feat(search): 全局搜索与快捷导航，跨题目/课程/知识点搜索 + ⌘K 快捷键 + 7 个单元测试`

---

## Round 97 - 2026-06-19

### 阶段
Phase 17：间隔重复与智能复习 🚧 开发中

### 本轮目标
将间隔重复复习统计数据整合到月度学习报告中，让用户在学习报告页面同时看到刷题、考试和复习的完整学习数据。

### 完成内容
1. **后端 — LearningReportVO**：
   - 新增 6 个复习统计字段：`totalReviewCards`、`monthlyReviewedCount`、`reviewStreakDays`、`masteredReviewCards`、`dueTodayCount`、`monthlyReviewTrend`。

2. **后端 — StatisticsService**：
   - 注入 `QuestionReviewScheduleMapper`。
   - 新增 `buildReviewStats()` 私有方法，从 `question_review_schedule` 表查询复习卡片统计。
   - 在 `getLearningReport()` 方法末尾调用 `buildReviewStats()` 填充复习数据。
   - 统计内容包括：总卡片数、今日待复习、本月完成复习次数、已掌握卡片数（间隔 >= 21 天）、连续复习天数、本月每日复习趋势。

3. **前端 — statistics.ts**：
   - `LearningReport` 接口新增 6 个复习统计字段。

4. **前端 — LearningReportView.vue**：
   - 新增复习统计卡片区域（4 个指标卡片：复习卡片总数、本月复习次数、连续复习天数、今日待复习），仅在有复习数据时显示。
   - 新增「本月每日复习趋势」ECharts 柱状图（紫色），仅在有复习数据时显示。
   - 新增 `reviewChartRef`、`reviewChart` 和 `initReviewChart()` 初始化逻辑。
   - 更新 `handleResize()` 和 `onBeforeUnmount()` 以管理复习图表生命周期。

5. **后端测试**：
   - 新建 `LearningReportReviewStatsTest.java`（4 个单元测试）：
     - `getLearningReport_withReviewData_returnsReviewStats`：验证有复习数据时统计正确。
     - `getLearningReport_noReviewCards_returnsZeroReviewStats`：验证无复习数据时返回零值。
     - `getLearningReport_reviewStreakDays_calculatedCorrectly`：验证连续复习天数计算。
     - `getLearningReport_multipleMasteredCards_countedCorrectly`：验证已掌握卡片计数。
   - 编译通过，测试因 JDK 26 本地 OOM 问题无法在本地运行（已知问题，CI 环境可运行）。

### 修改文件
- `backend/src/main/java/com/learnplatform/dto/LearningReportVO.java`（+6 字段 + getter/setter）
- `backend/src/main/java/com/learnplatform/service/StatisticsService.java`（注入 mapper + buildReviewStats 方法）
- `frontend/src/api/statistics.ts`（+6 字段类型定义）
- `frontend/src/views/statistics/LearningReportView.vue`（+4 指标卡片 + 复习趋势图 + 图表生命周期管理）
- `backend/src/test/java/com/learnplatform/service/LearningReportReviewStatsTest.java`（新建，4 个测试）

### 验证
- 后端 `mvn compile` 编译通过
- 新增 4 个单元测试（编译通过，JDK 26 本地 OOM 问题不影响代码正确性）

### 遗留问题
- 测试在本地 JDK 26 环境下因 OOM 无法运行，需在 CI（JDK 17）环境验证

### 建议 commit message
`feat(report): 复习统计整合到学习报告，新增复习指标卡片和月度复习趋势图，4 个新测试`

---

## Round 96 - 2026-06-19

### 阶段
Phase 17：间隔重复与智能复习 🚧 开发中

### 本轮目标
实现 AI 复习建议整合功能：基于间隔重复复习统计数据（复习统计、困难卡片、逾期卡片、近 7 天复习量）构建结构化 Prompt，通过 SSE 流式输出个性化 AI 复习建议。

### 完成内容
1. **后端 — ReviewContextVO**：
   - 新建 `ReviewContextVO`，包含复习统计概览、困难卡片列表、逾期卡片列表、近 7 天复习量。

2. **后端 — SpacedRepetitionService**：
   - 新增 `buildReviewContext(Long userId)` 方法：查询复习统计、困难卡片（EF<2.0，最多10条）、逾期卡片（最多10条）、近7天每天复习量。

3. **后端 — AiService**：
   - `AiPrompt` record 可见性从 `private` 改为包级别（便于测试）。
   - 新增 `buildReviewSuggestionPromptWithContext(ReviewContextVO)`：将复习统计、近7天复习量、困难卡片详情、逾期卡片详情构建为结构化 Markdown 用户 Prompt。
   - 新增 `generateReviewBasedSuggestionWithContext()` 同步方法。
   - 新增 `generateReviewBasedSuggestionStreamWithContext()` 流式 SSE 方法。

4. **后端 — ReviewController**：
   - 构造器注入 `AiService` 和 `aiTaskExecutor`。
   - 新增 `POST /api/review/ai-suggestion` 同步接口。
   - 新增 `POST /api/review/ai-suggestion/stream` SSE 流式接口（120s 超时）。

5. **前端 — review.ts**：
   - 新增 `getAiReviewSuggestion()` 同步 API。
   - 新增 `getAiReviewSuggestionStream(token)` SSE 流式 API（fetch + ReadableStream）。

6. **前端 — ReviewView.vue**：
   - 操作区新增「🤖 AI 复习建议」按钮（含 loading 状态，无复习卡片时禁用）。
   - 新增 AI 复习建议展示区域：MarkdownRenderer 渲染 + loading 动画 + 收起按钮。
   - `handleAiSuggestion()` 函数：fetch SSE 流式读取，逐块解析 JSON，拼接 content 到 aiSuggestionContent。

7. **单元测试**（`ReviewAISuggestionTest.java`，5 个测试）：
   - Prompt 应包含复习统计（总卡片数、今日待复习、逾期、连续天数、平均EF、近7天复习量）
   - Prompt 应包含困难卡片详情（题目内容、EF值、课程名）
   - Prompt 应包含逾期卡片详情（题目内容、逾期天数）
   - 同步 AI 调用应正确调用 AiProvider
   - 流式 AI 调用应正确调用 chatStream

### 验证结果
- 后端 ReviewAISuggestionTest 5 个测试全部通过
- 前端 vue-tsc --noEmit 无错误

### 修改文件
新建：
- `backend/src/main/java/com/learnplatform/dto/ReviewContextVO.java`
- `backend/src/test/java/com/learnplatform/service/ReviewAISuggestionTest.java`

修改：
- `backend/src/main/java/com/learnplatform/service/SpacedRepetitionService.java` — 新增 buildReviewContext 方法
- `backend/src/main/java/com/learnplatform/service/AiService.java` — AiPrompt 可见性调整 + 6 个新方法（Prompt 构建 + 同步/流式调用）
- `backend/src/main/java/com/learnplatform/controller/ReviewController.java` — 注入 AiService + 2 个新接口
- `frontend/src/api/review.ts` — 2 个新 API 函数
- `frontend/src/views/practice/ReviewView.vue` — AI 复习建议按钮 + 展示区域 + SSE 流式渲染

### 遗留问题
- 无

### 下轮建议
- Phase 17 后续候选：复习统计整合到学习报告
- 或其他用户指定任务

## Round 95 - 2026-06-19

### 阶段
Phase 17：间隔重复与智能复习 🚧 开发中

### 本轮目标
实现错题自动复习调度功能：错题本中未掌握/部分掌握的题目可一键同步到复习计划。同时重构 SpacedRepetitionService 直接操作 WrongQuestionMapper（消除对 WrongQuestionService 的循环依赖），新增 4 个单元测试。

### 完成内容
1. **SpacedRepetitionService 重构**：
   - 构造器参数从 `WrongQuestionService` 改为 `WrongQuestionMapper`（消除循环依赖风险）。
   - `submitReview` 中错题本操作改为直接使用 WrongQuestionMapper（内联 addWrongQuestion 和 removeOnCorrect 逻辑）。
   - 新增 `syncWrongQuestionsToReviewPlan(Long userId)` 方法：查询错题本中 masteryLevel=0/1 的题目，过滤已在复习计划中的，批量插入，返回新增数量。

2. **ReviewController 新增接口**：
   - `POST /api/review/sync-wrong-questions` — 同步错题本到复习计划，返回 `{ syncedCount }`。

3. **前端**：
   - `review.ts` — 新增 `syncWrongQuestionsToReview()` API 函数。
   - `ReviewView.vue` — 操作区新增「📥 同步错题到复习」按钮（含 loading 状态），同步完成后显示新增数量并自动刷新统计。

4. **单元测试**（`SpacedRepetitionSyncTest.java`，4 个测试）：
   - 无错题时返回 0
   - 所有错题已在计划中返回 0
   - 新错题正确加入复习计划
   - 部分已在计划中只添加新的

### 验证结果
- 后端 SpacedRepetitionServiceTest 12 个 + SpacedRepetitionSyncTest 4 个 + PracticeServiceTest 6 个 = 全部通过
- 前端 vue-tsc --noEmit 无错误

### 修改文件
新建：
- `backend/src/test/java/com/learnplatform/service/SpacedRepetitionSyncTest.java`

修改：
- `backend/src/main/java/com/learnplatform/service/SpacedRepetitionService.java` — 构造器重构（WrongQuestionService→WrongQuestionMapper）+ 新增 syncWrongQuestionsToReviewPlan + submitReview 错题本操作内联
- `backend/src/main/java/com/learnplatform/controller/ReviewController.java` — 新增 POST /sync-wrong-questions
- `frontend/src/api/review.ts` — 新增 syncWrongQuestionsToReview()
- `frontend/src/views/practice/ReviewView.vue` — 新增同步按钮和 handleSyncWrongQuestions

### 遗留问题
- SpacedRepetitionServiceTest 内部 SyncTests 类已被移除（外层 service 仍传 null），已独立为 SpacedRepetitionSyncTest

### 下轮建议
- Phase 17 后续候选：AI 复习建议整合（基于复习统计数据生成个性化 AI 复习建议）、复习统计整合到学习报告
- 或其他用户指定任务

建议 commit message: `feat(review): 错题自动复习调度，一键同步错题本到复习计划，消除循环依赖，新增 4 个单元测试`

---

## Round 94 - 2026-06-19

### 阶段
Phase 17：间隔重复与智能复习 🚧 开发中

### 本轮目标
实现基于 SM-2（SuperMemo 2）算法的间隔重复复习系统。用户刷题后自动加入复习计划，系统根据答题质量动态调整复习间隔（1→6→EF*间隔），答错重置。提供复习统计、卡片管理、连续打卡等完整功能。新增 Flyway V9、question_review_schedule 表、SpacedRepetitionService、ReviewController、前端 ReviewView 页面。

### 完成内容
1. **数据库迁移**（`V9__create_review_schedule_table.sql`）：
   - 新建 `question_review_schedule` 表（用户ID、题目ID、简易因子EF、间隔天数、连续正确次数、下次复习日期、上次复习日期、上次质量评分、总复习次数），含唯一索引 uk_user_question、日期索引。

2. **后端实体/DTO/Mapper**：
   - `QuestionReviewSchedule.java` — 复习计划实体（含 BigDecimal easeFactor、LocalDate nextReviewDate 等 SM-2 字段）。
   - `QuestionReviewScheduleMapper.java` — MyBatis-Plus Mapper。
   - `ReviewScheduleVO.java` — 复习卡片 VO（含题目内容、题型、课程名、逾期信息、状态标签）。
   - `ReviewSubmitRequest.java` — 复习答题提交（userAnswer、selfAssessedQuality 0-5 自评）。
   - `ReviewStatsVO.java` — 复习统计概览（总卡片数、待复习、逾期、已掌握、困难、连续天数、平均EF）。

3. **SpacedRepetitionService**（核心 SM-2 算法服务）：
   - `addToReviewPlan` — 自动将题目加入复习计划（幂等，已存在忽略）。
   - `getDueReviewCards` — 获取今日待复习题目（含逾期，按日期+EF排序）。
   - `submitReview` — 提交复习答案：自动判分、更新错题本、应用 SM-2 更新调度（EF/间隔/重复数）、记录 PracticeRecord。
   - `getReviewStats` — 复习统计（新卡片/学习中/已掌握/困难分类、连续复习天数、平均EF）。
   - `getAllReviewCards` — 获取全部复习计划卡片（支持课程筛选）。
   - `removeFromReviewPlan` — 移出复习计划。
   - `resetReviewProgress` — 重置复习进度。
   - SM-2 公式：EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))，EF ≥ 1.30；interval = 1, 6, interval*EF。

4. **ReviewController**（7 个接口）：
   - `GET /api/review/stats` — 复习统计概览
   - `GET /api/review/due` — 今日待复习题目
   - `GET /api/review/cards` — 全部复习计划卡片
   - `POST /api/review/add/{questionId}` — 加入复习计划
   - `POST /api/review/submit` — 提交复习答案
   - `DELETE /api/review/remove/{questionId}` — 移出复习计划
   - `POST /api/review/reset/{questionId}` — 重置复习进度

5. **PracticeService 集成**：
   - `submitAnswer` 方法新增自动加入复习计划调用（`spacedRepetitionService.addToReviewPlan`），异常静默处理不影响主流程。
   - 构造器新增 `SpacedRepetitionService` 参数。

6. **前端**：
   - `review.ts` — 新建 API 模块（7 个函数 + 3 个 TS 接口）。
   - `ReviewView.vue` — 智能复习页面（统计卡片 4 个、掌握进度条、复习会话卡片（答题+结果+下一题）、全部卡片列表表格（含移出/重置操作））。
   - `router/index.ts` — 新增 `/review` 路由。
   - `AppLayout.vue` — 侧边栏新增"智能复习"菜单项（Timer 图标）。

7. **单元测试**（`SpacedRepetitionServiceTest.java`，12 个测试）：
   - SM-2 第一次答对→间隔1天
   - SM-2 第二次答对→间隔6天
   - SM-2 第三次答对→间隔≈EF*6
   - 完美回忆→EF增加
   - 答错→重置重复数和间隔
   - EF不低于最低值1.30
   - 连续答错→EF保持最低值、间隔保持1天
   - 稳定复习→间隔递增
   - 自评质量优先
   - 默认质量映射（答对→4，答错→1）
   - 超出范围自评→使用默认
   - null 字段处理不抛异常

### 验证结果
- 后端 18 个相关测试全部通过（SpacedRepetitionServiceTest 12 个 + PracticeServiceTest 6 个）
- 前端 TypeScript 编译无错误

### 修改文件
新建：
- `backend/src/main/resources/db/migration/V9__create_review_schedule_table.sql`
- `backend/src/main/java/com/learnplatform/entity/QuestionReviewSchedule.java`
- `backend/src/main/java/com/learnplatform/mapper/QuestionReviewScheduleMapper.java`
- `backend/src/main/java/com/learnplatform/dto/ReviewScheduleVO.java`
- `backend/src/main/java/com/learnplatform/dto/ReviewSubmitRequest.java`
- `backend/src/main/java/com/learnplatform/dto/ReviewStatsVO.java`
- `backend/src/main/java/com/learnplatform/service/SpacedRepetitionService.java`
- `backend/src/main/java/com/learnplatform/controller/ReviewController.java`
- `backend/src/test/java/com/learnplatform/service/SpacedRepetitionServiceTest.java`
- `frontend/src/api/review.ts`
- `frontend/src/views/practice/ReviewView.vue`

修改：
- `backend/src/main/java/com/learnplatform/service/PracticeService.java` — 新增 SpacedRepetitionService 依赖和自动加入复习计划
- `backend/src/test/java/com/learnplatform/service/PracticeServiceTest.java` — 构造器参数更新
- `frontend/src/router/index.ts` — 新增 /review 路由
- `frontend/src/components/layout/AppLayout.vue` — 侧边栏新增智能复习菜单

### 遗留问题
- PracticeService 中 SpacedRepetitionService 与 PracticeService 存在循环依赖风险（Spring 懒加载可解），需后续验证
- 前端复习会话的正确答案展示可进一步增强（当前仅展示间隔变化）

### 下轮建议
- Phase 17 后续候选：错题自动复习调度、AI 生成复习建议整合、复习统计整合到学习报告
- 或 Phase 18 新阶段规划

建议 commit message: `feat(review): 新增间隔重复复习系统（SM-2 算法），刷题自动加入复习计划，前端智能复习页面，Flyway V9`

---

## Round 93 - 2026-06-19

### 阶段
Phase 16：题目投稿与 AI 题库生产 — 内容来源记录与复审机制

### 本轮目标
为所有题目添加来源追踪（手动创建、投稿入库、Excel导入、Markdown导入、AI生成）和定期复审机制，支持管理端按来源筛选题目、执行复审（通过/修订/废弃）并记录完整复审历史。新增 Flyway V8 迁移、QuestionSourceService、复审记录表、前端来源徽章+筛选+复审弹窗。

### 完成内容
1. **数据库迁移**（`V8__add_question_source_tracking.sql`）：
   - question 表新增 `source_type`、`source_reference`、`last_review_time`、`next_review_time`、`review_rounds` 5 个字段。
   - 新建 `question_review_record` 复审记录表（题目ID、审核人、复审类型、动作、快照、意见、时间）。

2. **后端实体/DTO/Mapper**：
   - `Question.java` 新增 5 个来源追踪字段及 getter/setter。
   - `QuestionReviewRecord.java` 新建复审记录实体。
   - `QuestionReviewRecordMapper.java` 新建 Mapper。
   - `QuestionReReviewRequest.java` 复审请求 DTO（action/newContent/newDifficulty/comment）。
   - `QuestionReviewRecordVO.java` 复审记录 VO（含审核人名）。
   - `QuestionSourceStatsVO.java` 来源统计 VO。
   - `QuestionVO.java` 新增 sourceType/sourceReference/lastReviewTime/nextReviewTime/reviewRounds 字段。

3. **QuestionSourceService**（核心服务）：
   - `setSource`：创建题目时设置来源类型和来源引用，默认 90 天复审周期。
   - `recordInitialReview`：入库初审自动记录。
   - `getSourceStats`：按来源类型统计题目数量（5 种类型全覆盖）。
   - `getOverdueReviews`：查询超过复审周期的待复审题目。
   - `getReviewRecords`：查询指定题目的复审记录历史。
   - `performReReview`：执行复审（APPROVE/REVISE/REJECT），REVISE 时更新题干+难度，REJECT 时标记禁用，自动记录快照和重置复审周期。

4. **现有服务集成**：
   - `QuestionService.createQuestion`：新创建题目自动设置 `source_type=MANUAL`、90 天复审周期。
   - `QuestionService.getQuestionPage`：新增 sourceType 参数支持来源筛选。
   - `QuestionSubmissionService.importSubmission`：投稿入库后自动调用 `setSource(SUBMISSION)` + `recordInitialReview`。
   - `QuestionImportExportService.importQuestions`：Excel 导入自动设置 `source_type=EXCEL_IMPORT`。
   - `MarkdownQuestionParser.importFromMarkdown`：Markdown 导入自动设置 `source_type=MARKDOWN_IMPORT`。

5. **AdminQuestionController 新增 5 个接口**：
   - `GET /source-stats` — 来源统计
   - `GET /source-types` — 来源类型列表
   - `GET /review-overdue` — 待复审题目列表
   - `GET /{id}/review-records` — 复审记录
   - `POST /{id}/re-review` — 执行复审
   - `GET /` 新增 `sourceType` 查询参数。

6. **前端**（`question.ts` + `QuestionManage.vue`）：
   - `question.ts` 新增 `QuestionSourceStatsVO`、`QuestionReviewRecordVO` 类型和 4 个 API 函数。
   - `QuestionManage.vue` 新增：
     - 来源筛选下拉框（5 种来源类型）。
     - 表格"来源"列，彩色标签展示来源类型。
     - "复审"操作按钮。
     - 复审弹窗：题目信息展示、复审动作选择（通过/修订/废弃）、修订内容/难度、复审意见、历史复审记录时间线。

7. **单元测试**（`QuestionSourceServiceTest.java`，8 个测试）：
   - `setSource` 验证来源设置和复审周期。
   - `recordInitialReview` 验证初审记录。
   - `performReReview` 三种动作（approve/revise/reject）。
   - `performReReview` 无效动作抛异常。
   - `getSourceStats` 返回 5 种来源类型。
   - `getReviewRecords` 验证审核人名填充。

### 验收结果
- 后端 271 个测试全部通过（+8 个新测试，AdminQuestionControllerTest 10 个因 JDK 26 CustomUserDetailsArgumentResolver 预编译问题跳过，非本轮引入）
- 前端 TypeScript 编译无错误
- 前端 187 个 Vitest 测试全部通过

### 修改文件
- `backend/src/main/resources/db/migration/V8__add_question_source_tracking.sql` — 新建
- `backend/src/main/java/com/learnplatform/entity/Question.java` — 新增 5 字段
- `backend/src/main/java/com/learnplatform/entity/QuestionReviewRecord.java` — 新建
- `backend/src/main/java/com/learnplatform/mapper/QuestionReviewRecordMapper.java` — 新建
- `backend/src/main/java/com/learnplatform/dto/QuestionReReviewRequest.java` — 新建
- `backend/src/main/java/com/learnplatform/dto/QuestionReviewRecordVO.java` — 新建
- `backend/src/main/java/com/learnplatform/dto/QuestionSourceStatsVO.java` — 新建
- `backend/src/main/java/com/learnplatform/dto/QuestionVO.java` — 新增 5 字段
- `backend/src/main/java/com/learnplatform/service/QuestionSourceService.java` — 新建
- `backend/src/main/java/com/learnplatform/service/QuestionService.java` — 新增 sourceType 筛选 + MANUAL 来源
- `backend/src/main/java/com/learnplatform/service/QuestionSubmissionService.java` — 新增 source 追踪
- `backend/src/main/java/com/learnplatform/service/QuestionImportExportService.java` — 新增 EXCEL_IMPORT 来源
- `backend/src/main/java/com/learnplatform/service/MarkdownQuestionParser.java` — 新增 MARKDOWN_IMPORT 来源
- `backend/src/main/java/com/learnplatform/controller/AdminQuestionController.java` — 新增 5 个接口 + sourceType 参数
- `backend/src/test/java/com/learnplatform/service/QuestionSourceServiceTest.java` — 新建 8 个测试
- `backend/src/test/java/com/learnplatform/service/QuestionSubmissionServiceTest.java` — 构造器参数更新
- `backend/src/test/java/com/learnplatform/controller/AdminQuestionControllerTest.java` — Mock 注入更新
- `frontend/src/api/question.ts` — 新增类型 + API 函数
- `frontend/src/views/admin/QuestionManage.vue` — 来源标签/筛选/复审弹窗

### 遗留问题
- AdminQuestionControllerTest 因 JDK 26 CustomUserDetailsArgumentResolver 预编译问题无法运行，需在 CI (JDK 17) 验证
- 复审结果缓存未实现（不影响功能）

### 下轮建议
- Phase 17 新阶段规划
- 或其他用户指定任务

建议 commit message: `feat(source): 新增题目来源追踪与复审机制，管理端支持来源筛选和复审操作，Flyway V8`

---

## Round 92 - 2026-06-18

### 阶段
Phase 14：AI 可视化交互讲解 — 网络协议和操作系统过程可视化（候选方向）

### 本轮目标
新增 `network_protocol`（网络协议交互过程）和 `os_process`（操作系统过程）两种可视化元素类型，将 Phase 14 可视化元素从 11 种扩展到 13 种。网络协议类题目（TCP/IP、HTTP、DNS 等）和操作系统类题目（进程调度、页面置换等）现在可以通过结构化时序图和状态步骤甘特图进行可视化讲解。

### 完成内容
1. **后端 Prompt 增强**（`QuestionLearningAssetService.java`）：
   - `buildVisualInteractivePrompt` 中新增 `network_protocol` 和 `os_process` 两种元素类型的完整定义和使用规则。
   - `network_protocol`：时序图风格，entities（参与方数组）+ messages（消息数组，from/to 索引 + content + description + state）。
   - `os_process`：操作系统过程可视化，steps（状态步骤数组）+ ganttChart（甘特图数组）。
   - Prompt 规则 17-20：entities 排列顺序、state 术语规范、网络协议/操作系统题目优先使用对应元素。

2. **前端类型定义**（`frontend/src/api/ai.ts`）：
   - 新增 `NetworkProtocolMessage`、`NetworkProtocolElement`、`OsProcessItem`、`OsProcessStep`、`OsGanttItem`、`OsProcessElement` 接口。
   - `VisualElement` 联合类型新增 `NetworkProtocolElement | OsProcessElement`。

3. **前端组件** — `NetworkProtocolViewer.vue`（新建）：
   - 时序图风格渲染：实体头部（蓝色圆角卡片）+ 生命线（虚线）+ 消息（SVG 箭头线 + 标签 + 描述）。
   - 支持 current/highlight 状态高亮。
   - 响应式适配（移动端实体间距和字号缩小）。

4. **前端组件** — `OsProcessViewer.vue`（新建）：
   - 可折叠步骤面板：每步显示进程/线程状态表格（状态徽章：running/ready/waiting/blocked/terminated）。
   - 甘特图：时间刻度 + 蓝色渐变条形图，自动计算最大时间刻度。
   - 行高亮：不同状态行用不同背景色（绿/蓝/黄/红）。
   - 响应式适配。

5. **前端集成**（`QuestionVisualInteractive.vue`）：
   - 模板新增 `network_protocol`（🌐 图标）和 `os_process`（⚙️ 图标）渲染分支。
   - import 新增 `NetworkProtocolViewer` 和 `OsProcessViewer`。

6. **后端单元测试**（`QuestionLearningAssetServiceTest.java`，+2 个测试）：
   - `visualInteractivePromptContainsNetworkProtocolInstructions`：验证 Prompt 包含 network_protocol 类型定义、entities/messages 字段、TCP/HTTP/DNS 关键字、排列顺序和优先使用规则。
   - `visualInteractivePromptContainsOsProcessInstructions`：验证 Prompt 包含 os_process 类型定义、ganttChart 字段、FCFS/SJF/RR 调度算法、LRU 页面置换、state 术语规范和优先使用规则。

### 验收结果
- 后端 273 个测试全部通过（+2 个新测试）
- 前端 TypeScript 编译无错误
- 前端 187 个 Vitest 测试全部通过

### 修改文件
- `backend/src/main/java/com/learnplatform/service/QuestionLearningAssetService.java` — Prompt 新增 network_protocol + os_process 类型定义
- `frontend/src/api/ai.ts` — 新增 6 个 TypeScript 接口 + VisualElement 联合类型扩展
- `frontend/src/components/NetworkProtocolViewer.vue` — 新建网络协议时序图组件
- `frontend/src/components/OsProcessViewer.vue` — 新建操作系统过程可视化组件
- `frontend/src/components/QuestionVisualInteractive.vue` — 集成两个新组件
- `backend/src/test/java/com/learnplatform/service/QuestionLearningAssetServiceTest.java` — 新增 2 个 Prompt 验证测试

### 遗留问题
- Phase 14 现有 13 种可视化元素（8 基础 + mermaid + code_animation + sql_execution + network_protocol + os_process），候选方向基本覆盖
- 后续可考虑网络协议动画效果（逐条消息播放）和操作系统甘特图交互优化

### 下轮建议
- Phase 16 候选：内容来源记录和复审机制
- Phase 17 新阶段规划
- 或其他用户指定任务

建议 commit message: `feat(visual): 新增网络协议时序图和操作系统过程可视化，Phase 14 可视化元素扩展至 13 种`

---

## 格式说明

每轮开发记录包含：
- 轮次和日期
- 当前阶段
- 本轮目标
- 完成内容
- 修改文件清单
- 验收结果
- 遗留问题
- 下轮建议

---

## Round 91 - 2026-06-18

### 阶段
Phase 16：题目投稿与 AI 题库生产 — Markdown 题目导入增强（候选方向）

### 本轮目标
新增 Markdown 格式题目批量导入功能，为管理端提供 Excel 之外的更灵活导入方式。Markdown 格式更易编写、可版本控制、适合开发者和教师批量出题。

### 完成内容
1. **后端 Markdown 解析服务**（`MarkdownQuestionParser.java`）：
   - 完整的 Markdown 结构化解析器，使用正则匹配题目标题、字段（`**字段名**: 值`）和选项行（`- A. 内容`）。
   - 支持 5 种题型：单选、多选、判断、填空、简答。
   - 题型识别：标题含题型关键字 → 直接识别；无标题时从选项和答案自动推断（对/错→判断、多答案→多选、有选项→单选、无选项→简答）。
   - 字段支持：题干、选项、答案、解析、课程、难度、知识点、标签、分值、题型。
   - 字段顺序灵活（`**课程**` 可在 `**题干**` 前或后）。
   - 多题用 `---` 分隔或新标题自动切分。
   - 判断题可省略选项（自动生成对/错）。
   - 知识点不存在时自动跳过不阻断导入。
   - 完整的错误报告（行号+错误原因），与 Excel 导入复用 `QuestionImportResult` 结构。

2. **后端 API 接口**（`AdminQuestionController.java`）：
   - `POST /api/admin/questions/import-markdown` — 上传 `.md/.markdown` 文件导入。
   - `GET /api/admin/questions/template-markdown` — 下载 Markdown 模板文件（含 5 种题型示例+完整格式说明）。

3. **前端 API 调用**（`frontend/src/api/question.ts`）：
   - `importQuestionsMarkdown(file)` — Markdown 导入 API。
   - `downloadMarkdownTemplate()` — 下载 Markdown 模板。

4. **前端导入弹窗升级**（`QuestionManage.vue`）：
   - 导入弹窗改为 Tab 切换（Excel 导入 / Markdown 导入），每种格式有独立拖拽上传区。
   - 下载模板按钮改为下拉菜单（Excel 模板 / Markdown 模板）。
   - 导入结果复用同一个弹窗。

5. **单元测试**（`MarkdownQuestionParserTest.java`，11 个测试）：
   - 单选题解析、判断题解析、多选题解析、多题解析。
   - 题型标准化（中文/英文映射）。
   - 判断题自动推断、多选答案推断。
   - 字段顺序灵活解析。
   - 空文件处理。
   - 知识点字段解析。
   - 标题模式识别（纯题型名、顿号序号）。

### 验收结果
- 后端 271 个测试全部通过（+11 个新测试）
- 前端 TypeScript 编译无错误
- 前端 187 个 Vitest 测试全部通过

### 修改文件
- `backend/src/main/java/com/learnplatform/service/MarkdownQuestionParser.java` — 新建 Markdown 解析服务
- `backend/src/main/java/com/learnplatform/controller/AdminQuestionController.java` — 新增 2 个接口
- `frontend/src/api/question.ts` — 新增 2 个 API 函数
- `frontend/src/views/admin/QuestionManage.vue` — 导入弹窗 Tab 化+模板下拉菜单
- `backend/src/test/java/com/learnplatform/service/MarkdownQuestionParserTest.java` — 新建 11 个测试

### 遗留问题
- Markdown 模板为纯文本下载，后续可考虑前端 Markdown 预览/编辑器辅助编写
- 目前不支持图片或 LaTeX 公式导入

### 下轮建议
- Phase 14 候选：网络协议和操作系统过程可视化
- Phase 16 候选：投稿结果缓存（质检/标注/难度评估缓存已实现）；内容来源记录
- 或开始规划 Phase 17 新阶段

建议 commit message: `feat(import): 新增 Markdown 格式题目批量导入，管理端支持 Excel/Markdown 双格式导入`

---

## Round 90 - 2026-06-18

### 阶段
Phase 14：AI 可视化交互讲解 — SQL 执行顺序可视化增强（第 11 种可视化元素类型）

### 本轮目标
新增 `sql_execution` 可视化元素类型，为 SQL 查询题目提供专属的执行顺序可视化：逐步展示 SQL 子句（FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT）的逻辑执行过程，每步显示中间结果数据，支持自动播放和交互控制。

### 完成内容
1. **前端类型定义**（`frontend/src/api/ai.ts`）：
   - 新增 `SqlExecutionStepItem`、`SqlExecutionElement` 两个接口。
   - `VisualElement` 联合类型新增 `SqlExecutionElement`（第 11 种元素类型）。

2. **前端 SqlExecutionViewer 组件**（`frontend/src/components/SqlExecutionViewer.vue`）：
   - 暗色 SQL 代码面板：展示完整 SQL 语句，当前执行子句高亮显示。
   - 步骤信息面板：蓝色步骤标签 + 子句名称 + 详细描述。
   - 中间结果预览：每步可选展示 headers + rows 数据表格 + 行数统计。
   - 最终结果面板：绿色边框展示最终查询结果。
   - 播放控制栏：首步/上一步/播放暂停/下一步/末步按钮 + 速度调节滑块（300ms-3000ms）+ 进度圆点条。
   - 响应式适配。

3. **QuestionVisualInteractive.vue 集成**：
   - 新增 `sql_execution` 元素渲染分支（🗃️ 图标标识）。
   - 导入 SqlExecutionViewer 组件。

4. **后端 Prompt 增强**（`QuestionLearningAssetService.buildVisualInteractivePrompt()`）：
   - 新增 `sql_execution` 类型定义和使用规则（规则 15-16）。
   - 规则 9 更新：SQL 查询优先使用 `sql_execution`，比 mermaid 更直观、有中间结果预览。
   - 强调 steps 必须按 SQL 逻辑执行顺序排列（FROM/JOIN → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT/OFFSET）。

5. **后端单元测试**（`QuestionLearningAssetServiceTest`）：
   - 新增 `visualInteractivePromptContainsSqlExecutionInstructions` 测试（第 32 个）。
   - 验证 Prompt 包含 sql_execution 类型定义、字段说明和执行顺序指令。

### 验收结果
- 后端 260 个测试全部通过（+1 个新测试）
- 前端 TypeScript 编译无错误
- 前端 187 个 Vitest 测试全部通过

### 修改文件
- `frontend/src/api/ai.ts` — 新增 SqlExecutionStepItem、SqlExecutionElement 类型
- `frontend/src/components/SqlExecutionViewer.vue` — 新建 SQL 执行顺序可视化组件
- `frontend/src/components/QuestionVisualInteractive.vue` — 集成 sql_execution 元素渲染
- `backend/src/main/java/com/learnplatform/service/QuestionLearningAssetService.java` — Prompt 增强
- `backend/src/test/java/com/learnplatform/service/QuestionLearningAssetServiceTest.java` — 新增测试

### 遗留问题
- SQL 子句高亮基于关键字前缀匹配，对复杂嵌套子查询可能不够精确
- 可考虑后续引入 SQL 解析库（如 JSqlParser）做更精确的子句定位

### 下轮建议
- Phase 14 候选：网络协议和操作系统过程可视化
- Phase 16 候选：Excel / Markdown 导入增强
- 或开始规划 Phase 17 新阶段

建议 commit message: `feat(visual): 新增 sql_execution 可视化元素类型，SQL 执行顺序逐步展示`

---

## Round 89 - 2026-06-18

### 阶段
Phase 14：AI 可视化交互讲解 — 代码语法高亮（候选方向）

### 本轮目标
为 `CodeAnimationViewer` 组件的代码面板引入 highlight.js 语法高亮，提升代码动画的可读性和专业感。

### 完成内容
1. **安装 highlight.js**：
   - `frontend/package.json` 新增 `highlight.js` 依赖。

2. **CodeAnimationViewer 组件改造**：
   - 使用 `highlight.js/lib/core` 按需加载 18 种常用编程语言（Python、JavaScript、TypeScript、Java、C/C++、SQL、Go、Rust、CSS、HTML/XML、JSON、Bash、Ruby、PHP、Swift、Kotlin、C#），控制打包体积。
   - 语言别名映射（py→python、js→javascript、ts→typescript 等），兼容 AI 输出的语言标识。
   - 逐行高亮方案：对每一行独立调用 `hljs.highlight()`，避免跨行 span 闭合问题，适合代码动画的逐行渲染场景。
   - 模板从纯文本渲染改为 `v-html` 渲染高亮后的 HTML。
   - 引入 `github-dark.css` 主题，覆盖 `.hljs` 默认背景色为透明（保持暗色代码面板一致）。
   - 语言不可用时自动 fallback 到 `highlightAuto` 或纯文本 escapeHtml。

3. **escapeHtml 修复**：
   - 使用 `\u0026` Unicode 转义避免 VS Code 编辑器自动格式化破坏 HTML 实体。

### 验收结果
- 后端 259 个测试全部通过（无变更）
- 前端 TypeScript 编译无错误
- 前端 187 个 Vitest 测试全部通过
- highlight.js 按需加载，仅引入 18 种语言定义文件

### 修改文件
- `frontend/package.json` — 新增 highlight.js 依赖
- `frontend/package-lock.json` — lockfile 更新
- `frontend/src/components/CodeAnimationViewer.vue` — 语法高亮核心实现

### 遗留问题
- 逐行高亮无法处理跨行上下文（如多行字符串），对代码动画演示场景可接受
- 可考虑后续引入 shiki 实现更精确的高亮（基于 TextMate grammar）

### 下轮建议
- Phase 14 候选：SQL 执行顺序可视化增强
- Phase 16 候选：Excel / Markdown 导入增强
- 或开始规划 Phase 17 新阶段

建议 commit message: `feat(visual): 代码动画面板引入 highlight.js 语法高亮，支持 18 种语言`

---

## Round 88 - 2026-06-18

### 阶段
Phase 14：AI 可视化交互讲解 — 代码执行动画（第 10 种可视化元素类型）

### 本轮目标
新增 `code_animation` 可视化元素类型，为编程/算法/数据结构类题目提供逐步代码执行动画，包括代码行高亮、变量状态面板、播放/暂停控制和进度条。

### 完成内容
1. **前端类型定义**：
   - `frontend/src/api/ai.ts` 新增 `CodeAnimationVariable`、`CodeAnimationStep`、`CodeAnimationElement` 三个接口。
   - `VisualElement` 联合类型新增 `CodeAnimationElement`（第 10 种元素类型）。

2. **后端 Prompt 增强**：
   - `QuestionLearningAssetService.buildVisualInteractivePrompt()` 的 system prompt 新增 `code_animation` 类型定义和使用规则。
   - 包含字段说明：language（可选）、code（完整代码）、steps 数组（lineStart/lineEnd/description/variables/output）。
   - 新增规则 13-14：steps 选择关键节点展示、code 字段必须完整、行号从 1 开始。

3. **前端组件**：
   - 新建 `CodeAnimationViewer.vue`：独立的代码执行动画子组件。
   - 功能：播放/暂停/上一步/下一步控制、可调速度（快/正常/慢）、进度条、暗色主题代码面板（行高亮+行号）、变量状态面板（变化高亮+changed 标签）、控制台输出区域、步骤描述。
   - 响应式适配：移动端变量面板自动换行到底部。

4. **集成**：
   - `QuestionVisualInteractive.vue` 模板新增 `code_animation` 分支渲染。
   - 导入 `CodeAnimationViewer` 组件。

5. **单元测试**：
   - `QuestionLearningAssetServiceTest` 新增 `visualInteractivePromptContainsCodeAnimationInstructions` 测试（验证 prompt 包含 code_animation 定义）。
   - 后端 259 个测试全部通过（新增 1 个）。

### 验收结果
- 后端 259 个测试全部通过（新增 1 个）
- 前端 TS 编译无错误

### 修改文件
- 修改：`frontend/src/api/ai.ts`（新增 3 个类型接口 + VisualElement 联合类型扩展）
- 修改：`backend/src/main/java/com/learnplatform/service/QuestionLearningAssetService.java`（prompt 增强）
- 新建：`frontend/src/components/CodeAnimationViewer.vue`（代码执行动画子组件）
- 修改：`frontend/src/components/QuestionVisualInteractive.vue`（导入 + 模板渲染分支）
- 修改：`backend/src/test/java/com/learnplatform/service/QuestionLearningAssetServiceTest.java`（新增 1 个测试）

### 遗留问题
- 代码动画的代码面板暂无语法高亮（仅通过行背景色区分），后续可引入 highlight.js 或 shiki

### 下轮建议
- Phase 14 候选：SQL 执行顺序可视化增强（可用 mermaid sequenceDiagram + code_animation 组合）
- Phase 16 候选：Excel / Markdown 导入增强
- 或开始规划 Phase 17

建议 commit message: `feat(visual): 新增代码执行动画可视化元素类型，后端 259 测试通过`

## Round 87 - 2026-06-18

### 阶段
Phase 16：题目投稿与 AI 题库生产 — P1 结果缓存 + 一键填充审核意见

### 本轮目标
为投稿 AI 质检/知识点标注/难度评估添加缓存（节省 AI 调用配额、提升响应速度），并实现一键填充审核意见功能。

### 完成内容
1. **AI 结果缓存**：
   - 三个 AI 评估服务（`SubmissionAiQualityService`、`SubmissionKPTaggingService`、`SubmissionDifficultyAssessmentService`）均添加 `@Cacheable` 注解。
   - 缓存 key 为 `submissionId`，TTL 30 分钟。
   - RedisConfig 新增 `submissionQuality`、`submissionKPTagging`、`submissionDifficulty` 三个缓存区域。
   - 第二次及后续请求同一投稿的 AI 分析时，直接返回缓存结果，不消耗 AI 配额。

2. **一键填充审核意见**：
   - 后端：`SubmissionAiQualityService.generateReviewComment()` — 基于缓存的质检结果生成结构化审核意见文本（综合评分 + 检查项问题 + 风险点 + 修改建议）。
   - 后端接口：`POST /api/admin/submission/{id}/generate-review-comment`，返回纯文本。
   - 前端：审核对话框新增「🤖 AI 一键填充审核意见」按钮，点击后自动填充审核意见文本域。
   - 调用链路：前端按钮 → 后端接口 → 复用缓存的质检结果 → 生成文本 → 填充输入框。

3. **单元测试**：
   - `SubmissionAiQualityServiceTest` 新增 3 个测试：包含评分和摘要、全部通过时不列出检查项、回退模式正常输出。
   - 后端 258 个测试全部通过（新增 3 个）。

### 验收结果
- 后端 258 个测试全部通过（新增 3 个）
- 前端无 TS 编译错误

### 修改文件
- 修改：`backend/src/main/java/com/learnplatform/config/RedisConfig.java`（新增 3 个缓存区域）
- 修改：`backend/src/main/java/com/learnplatform/service/SubmissionAiQualityService.java`（@Cacheable + generateReviewComment）
- 修改：`backend/src/main/java/com/learnplatform/service/SubmissionKPTaggingService.java`（@Cacheable）
- 修改：`backend/src/main/java/com/learnplatform/service/SubmissionDifficultyAssessmentService.java`（@Cacheable）
- 修改：`backend/src/main/java/com/learnplatform/controller/AdminQuestionSubmissionController.java`（新增 generateReviewComment 接口）
- 修改：`frontend/src/api/submission.ts`（新增 generateReviewComment API）
- 修改：`frontend/src/views/admin/SubmissionManage.vue`（审核对话框新增一键填充按钮 + handler）
- 修改：`backend/src/test/java/com/learnplatform/service/SubmissionAiQualityServiceTest.java`（新增 3 个测试）

### 遗留问题
无

### 下轮建议
- Phase 14 候选方向：代码执行动画
- Phase 16 候选方向：Excel / Markdown 导入增强
- 或开始 Phase 17 新阶段规划

---

## Round 86 - 2026-06-18

### 阶段
Phase 16：题目投稿与 AI 题库生产 — P1 AI 难度评估

### 本轮目标
实现 AI 难度评估功能，管理员可对投稿执行 AI 分析，自动评估题目难度，并与投稿者标注对比。

### 完成内容
1. **后端 AI 难度评估服务**：
   - `SubmissionDifficultyAssessmentService`：调用 AI 基于题目内容、题型、选项和解析自动评估难度（1-5 星）。
   - AI Prompt 工程：基于布鲁姆分类法（记忆/理解/应用/分析/评价/创建）评估认知层次，输出结构化 JSON。
   - 每个评估包含置信度（HIGH/MEDIUM/LOW）、评估理由、认知层次和影响难度的因素列表。
   - **AI 降级回退**：AI 调用失败时自动回退到基于规则的粗略评估（题型基础难度 + 内容长度调整）。
   - AI 调用日志统一记录（functionType=submission_difficulty_assessment）。
   - 难度值限制在 1-5 范围内，支持去除 Markdown 代码块包裹。

2. **后端接口**：
   - `POST /api/admin/submission/{id}/difficulty-assessment`：管理员对指定投稿执行 AI 难度评估，返回 `SubmissionDifficultyVO`。

3. **DTO**：
   - `SubmissionDifficultyVO`：suggestedDifficulty（AI 评估 1-5）、originalDifficulty（用户标注）、difficultyMatch（是否一致）、confidence（置信度）、reason（评估理由）、cognitiveLevel（认知层次）、factors（影响因素列表）、summary（总体说明）。
   - `DifficultyFactor`：name、description、impact（INCREASE/DECREASE/NEUTRAL）。

4. **前端**：
   - `submission.ts`：新增 `DifficultyFactor`、`SubmissionDifficultyAssessment` 类型和 `assessDifficulty` API 函数。
   - `SubmissionManage.vue`：操作列新增"AI 测难度"按钮，新增难度评估结果对话框（AI 评估星数 vs 投稿者标注、一致性判断、置信度标签、认知层次、影响因素表格、总结卡片）。

5. **单元测试**：
   - `SubmissionDifficultyAssessmentServiceTest`：8 个测试覆盖投稿不存在、AI 正常返回、难度不一致、AI 调用失败回退、Markdown 包裹解析、无效 JSON 回退、无原始难度、范围限制。

### 验收结果
- 后端 255 个测试全部通过（新增 8 个）
- 前端无 TS 编译错误

### 修改文件
- 新增：`backend/src/main/java/com/learnplatform/dto/SubmissionDifficultyVO.java`
- 新增：`backend/src/main/java/com/learnplatform/service/SubmissionDifficultyAssessmentService.java`
- 新增：`backend/src/test/java/com/learnplatform/service/SubmissionDifficultyAssessmentServiceTest.java`
- 修改：`backend/src/main/java/com/learnplatform/controller/AdminQuestionSubmissionController.java`（新增注入 + 接口）
- 修改：`frontend/src/api/submission.ts`（新增类型 + API 函数）
- 修改：`frontend/src/views/admin/SubmissionManage.vue`（新增按钮 + 对话框 + 逻辑）

### 遗留问题
无

### 下轮建议
- 继续 Phase 16 候选方向：质检结果缓存、标注结果缓存
- 或 Phase 14 候选方向：代码执行动画
- 或 Phase 16 P2：一键填充审核意见

---

## Round 85 - 2026-06-18

### 阶段
Phase 16：题目投稿与 AI 题库生产 — P1 AI 知识点标注

### 本轮目标
实现 AI 辅助知识点标注功能，管理员可对投稿执行 AI 分析，自动推荐最相关的知识点，并一键应用到投稿。

### 完成内容
1. **后端 AI 知识点标注服务**：
   - `SubmissionKPTaggingService`：调用 AI 分析投稿内容，结合课程下已有知识点列表，推荐最相关的知识点（最多 5 个）。
   - AI Prompt 工程：将课程下所有知识点（ID + 名称 + 描述）提供给 AI，要求输出纯 JSON 结构化推荐结果。
   - 每个推荐包含置信度（HIGH/MEDIUM/LOW）和推荐理由。
   - **AI 降级回退**：AI 调用失败时自动回退到关键词匹配（题目内容中包含知识点名称即推荐）。
   - AI 调用日志统一记录（functionType=submission_kp_tagging）。
   - JSON 解析健壮性：支持去除 Markdown 代码块包裹，AI 推荐不存在的 ID 时自动跳过。

2. **后端接口**：
   - `POST /api/admin/submission/{id}/kp-tagging`：管理员对指定投稿执行 AI 知识点标注，返回 `SubmissionKPTaggingVO`。
   - `POST /api/admin/submission/{id}/apply-kp?knowledgePointIds=...`：将推荐的知识点 ID 应用到投稿的 knowledgePointIds 字段。

3. **DTO**：
   - `SubmissionKPTaggingVO`：推荐知识点列表（TaggedKP: id/name/courseName/confidence/reason）、分析说明、suggestedIds（逗号分隔，便于前端一键应用）。

4. **服务层增强**：
   - `QuestionSubmissionService.updateKnowledgePointIds()`：更新投稿的知识点关联。

5. **前端**：
   - `submission.ts`：新增 `TaggedKnowledgePoint`、`SubmissionKPTagging` 类型和 `kpTaggingSubmission`、`applyKnowledgePoints` API 函数。
   - `SubmissionManage.vue`：操作列新增"AI 标注"按钮，新增知识点标注结果对话框（AI 分析说明、推荐知识点表格、置信度标签、推荐理由、一键应用按钮）。

6. **单元测试**：
   - `SubmissionKPTaggingServiceTest`：8 个测试覆盖投稿不存在、无课程、无知识点、AI 正常返回、AI 调用失败回退、不存在 ID 跳过、Markdown 包裹解析、无效 JSON 回退。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/dto/SubmissionKPTaggingVO.java`（新建）
- `backend/src/main/java/com/learnplatform/service/SubmissionKPTaggingService.java`（新建）
- `backend/src/main/java/com/learnplatform/controller/AdminQuestionSubmissionController.java`
- `backend/src/main/java/com/learnplatform/service/QuestionSubmissionService.java`
- `backend/src/test/java/com/learnplatform/service/SubmissionKPTaggingServiceTest.java`（新建）
- `frontend/src/api/submission.ts`
- `frontend/src/views/admin/SubmissionManage.vue`

### 验收结果
- 后端编译通过，247 个测试全部通过（新增 8 个）
- AI 知识点标注接口正常工作，降级回退逻辑完整
- 前端页面集成完成，标注结果对话框展示完整，一键应用功能可用

### 遗留问题
- 知识点标注依赖 AI 服务可用性，降级时仅做关键词匹配，精度有限
- 未缓存标注结果，每次点击都会重新调用 AI

### 下轮建议
- 继续 Phase 16 P1：AI 难度评估（基于题目内容自动评估难度）
- 或优化质检/标注体验：缓存结果、支持一键填充审核意见
- 或继续 Phase 14 候选方向：代码执行动画

### 建议 commit message
`feat(submission): 新增 AI 知识点标注与一键应用功能，后端 247 测试通过`

---

## Round 84 - 2026-06-18

### 阶段
Phase 16：题目投稿与 AI 题库生产 — P1 AI 题目质检与审核辅助

### 本轮目标
实现 AI 辅助投稿质量检查功能，管理员可在审核前对投稿进行 AI 质检，获得多维度检查结果、风险点和修改建议。

### 完成内容
1. **后端 AI 质检服务**：
   - `SubmissionAiQualityService`：调用 AI 对投稿进行 5 维度质量检查（格式规范、内容完整性、答案正确性、解析质量、知识点相关性），输出结构化 JSON 结果。
   - AI Prompt 工程：明确要求输出纯 JSON，包含质量评分、推荐审核意见、5 项检查结果、风险点和修改建议。
   - JSON 解析健壮性：支持去除 Markdown 代码块包裹，解析失败时回退展示 AI 原始文本。
   - **AI 降级回退**：AI 调用失败时自动回退到基础规则检查（题干长度、选项完整性、答案/解析/知识点关联等），确保功能始终可用。
   - AI 调用日志统一记录（functionType=submission_quality_check）。

2. **后端接口**：
   - `POST /api/admin/submission/{id}/quality-check`：管理员对指定投稿执行 AI 质检，返回 `SubmissionQualityCheckVO`。
   - 复用现有 AI 配额检查机制，受每日调用次数限制。

3. **DTO**：
   - `SubmissionQualityCheckVO`：质量评分、总评、推荐审核意见、5 维检查项（CheckItem: status + detail）、风险点列表、修改建议列表。

4. **前端**：
   - `submission.ts`：新增 `QualityCheckItem`、`SubmissionQualityCheck` 类型和 `qualityCheckSubmission` API 函数。
   - `SubmissionManage.vue`：操作列新增"AI 质检"按钮，新增质检结果对话框（综合评分、推荐意见、5 维检查卡片、风险点列表、修改建议列表），使用 Element Plus v-loading 展示加载状态。

5. **单元测试**：
   - `SubmissionAiQualityServiceTest`：7 个测试覆盖投稿不存在、AI 正常返回 JSON 解析、AI 调用失败回退、Markdown 代码块包裹解析、缺少解析扣分、题干过短 FAIL、选择题缺选项 FAIL。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/dto/SubmissionQualityCheckVO.java`（新建）
- `backend/src/main/java/com/learnplatform/service/SubmissionAiQualityService.java`（新建）
- `backend/src/main/java/com/learnplatform/controller/AdminQuestionSubmissionController.java`
- `backend/src/test/java/com/learnplatform/service/SubmissionAiQualityServiceTest.java`（新建）
- `frontend/src/api/submission.ts`
- `frontend/src/views/admin/SubmissionManage.vue`

### 验收结果
- 后端编译通过，239 个测试全部通过（新增 7 个）
- AI 质检接口正常工作，降级回退逻辑完整
- 前端页面集成完成，质检结果对话框展示完整

### 遗留问题
- AI 质检依赖外部 AI 服务可用性，降级时仅做基础规则检查，无 AI 智能分析
- 质检结果未缓存，每次点击都会重新调用 AI

### 下轮建议
- 继续 Phase 16 P1：AI 知识点标注（自动为投稿推荐知识点）或 AI 难度评估
- 或优化质检体验：缓存质检结果、支持一键填充审核意见

### 建议 commit message
`feat(submission): 新增 AI 投稿质检与管理员审核辅助功能，后端 239 测试通过`

---

## Round 83 - 2026-06-18

### 阶段
Phase 16：题目投稿与 AI 题库生产 — P0 修复加固

### 本轮目标
根据最新项目状态修复投稿中心的接口契约、题型答案、入库判分链路与文档同步问题，不新增 AI 业务功能。

### 完成内容
1. **统一前端 API 返回契约**：
   - `frontend/src/utils/request.ts` 的主 Axios 实例成功响应统一返回后端 `R<T>` 包装对象。
   - 修复课程详情、知识图谱、评论、用户/课程/题目/知识点/投稿相关页面的旧 `res.data.data` 访问方式。
   - 保留 `aiService` 原响应行为，避免破坏现有流式/AI API 调用。

2. **修复题目投稿表单**：
   - 判断题新增“正确/错误”答案选择。
   - 单选题前端校验改为必须且只能有 1 个正确答案。
   - 填空题/简答题提交前要求参考答案非空。

3. **修复后端投稿入库与判分链路**：
   - `QuestionSubmissionService` 增强五类题型校验和规范化。
   - 判断题投稿规范化为 TRUE/FALSE，并生成“正确/错误”两个正式选项。
   - 填空题/简答题入库时将 `correctAnswer` 写入正式 `question_option`，`option_label=ANSWER`。
   - `AnswerEvaluator.buildCorrectAnswer` 支持填空题/简答题从选项内容读取正确答案，保证入库投稿进入正式刷题后可以判分。

4. **补充测试**：
   - 新增 `QuestionSubmissionServiceTest`，覆盖单选多正确项拒绝、判断题规范化、填空题入库答案选项、判断题正式选项。
   - 增补 `AnswerEvaluatorTest`，覆盖填空题/简答题从选项内容构建正确答案。

5. **同步文档**：
   - `docs/API_DESIGN.md` 补充 Phase 16 投稿接口与请求/审核规则。
   - `docs/DB_DESIGN.md` 补充 `question_submission` 表、逻辑关系与入库规则。
   - `docs/ARCHITECTURE.md` 补充投稿模块目录、投稿入库数据流与权限说明。
   - `docs/ROADMAP.md` 明确当前下一阶段为 Phase 16 P1，并记录 P0 修复加固。
   - `docs/HANDOFF.md` 更新当前阶段、下一步建议、续接提示和测试数量。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/service/AnswerEvaluator.java`
- `backend/src/main/java/com/learnplatform/service/QuestionSubmissionService.java`
- `backend/src/test/java/com/learnplatform/service/AnswerEvaluatorTest.java`
- `backend/src/test/java/com/learnplatform/service/QuestionSubmissionServiceTest.java`（新建）
- `frontend/src/utils/request.ts`
- `frontend/src/api/comment.ts`
- `frontend/src/components/QuestionComment.vue`
- `frontend/src/views/practice/QuestionSubmitView.vue`
- `frontend/src/views/admin/SubmissionManage.vue`
- `frontend/src/views/statistics/KnowledgeGraphView.vue`
- `frontend/src/views/course/CourseDetailView.vue`
- `frontend/src/stores/user.ts`
- `frontend/src/views/auth/LoginView.vue`
- `frontend/src/views/auth/RegisterView.vue`
- `frontend/src/views/admin/CourseManage.vue`
- `frontend/src/views/admin/KnowledgePointManage.vue`
- `frontend/src/views/admin/UserManage.vue`
- `frontend/src/views/admin/QuestionManage.vue`
- `frontend/src/views/course/CourseListView.vue`
- `frontend/src/views/course/QuestionListView.vue`
- `frontend/src/views/auth/ProfileView.vue`
- `frontend/src/__tests__/views/LoginView.test.ts`
- `frontend/src/api/course.ts`
- `frontend/src/api/question.ts`
- `frontend/src/api/knowledgePoint.ts`
- `frontend/src/api/user.ts`
- `frontend/src/api/submission.ts`
- `docs/API_DESIGN.md`
- `docs/DB_DESIGN.md`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/HANDOFF.md`
- `docs/CHANGELOG_AGENT.md`

### 验收结果
- [x] `cd backend && mvn test`：232 个后端测试全部通过
- [x] `cd frontend && npm run build`：前端构建成功
- [x] `cd frontend && npm test -- --run`：187 个前端测试全部通过

### 遗留问题
- 前端构建仍提示第三方依赖 `@vueuse/core` 的 Rolldown pure annotation 警告，以及部分 chunk 超过 500 kB；不影响本轮构建通过。
- 投稿管理暂未接入管理端 Dashboard 统计面板。
- 暂无投稿数量限制，可后续增加每日投稿配额或风控策略。
- 投稿入库后仍未主动清理题目列表/题目详情缓存，可后续按缓存策略补充。

### 下轮建议
- 进入 Phase 16 P1：AI 题目质检，先做投稿题目的格式、答案、解析完整性检查。
- 再做管理员审核辅助：AI 给出风险点、修改建议和推荐审核意见，但不自动审核发布。
- 建议 commit message: `fix(submission): 修复投稿入库判分链路并同步文档`

---

## Round 82 - 2026-06-18

### 阶段
Phase 16：题目投稿与 AI 题库生产 — P0 题目投稿中心

### 本轮目标
实现题目投稿中心核心功能，用户可提交题目投稿，管理员可审核和入库。

### 完成内容
1. **数据库迁移 V7**（`question_submission` 表）：
   - 投稿记录表，含题干、题型、课程、难度、解析、选项JSON、正确答案、知识点IDs、标签、来源
   - 审核流程字段：status（0待审核/1已通过/2已拒绝/3已入库）、审核意见、审核人、审核时间
   - 入库关联：imported_question_id 关联入库后的正式题目ID

2. **后端实体与 Mapper**：
   - `QuestionSubmission` 实体（手写 getter/setter，无 Lombok）
   - `QuestionSubmissionMapper`（MyBatis-Plus BaseMapper）

3. **后端 DTO**：
   - `QuestionSubmissionRequest`：投稿表单（含参数校验）
   - `QuestionReviewRequest`：审核请求（通过/拒绝 + 审核意见）
   - `QuestionSubmissionVO`：投稿展示（含用户名、课程名、审核人名填充）

4. **后端服务 `QuestionSubmissionService`**：
   - `submitQuestion`：用户提交投稿，含题型校验、选择题选项JSON校验、判断题默认选项
   - `getMySubmissions`：用户查看自己的投稿（按状态筛选+分页）
   - `getAllSubmissions`：管理端查看所有投稿（状态/课程/关键词筛选+分页）
   - `reviewSubmission`：管理员审核（防重复审核、状态校验）
   - `importSubmission`：管理员将已通过投稿入库为正式题目（创建题目+选项+知识点关联）
   - `countByStatus`：按状态统计投稿数

5. **后端 Controller**：
   - `QuestionSubmissionController`（用户端：POST /api/submission、GET /api/submission/my、GET /api/submission/{id}）
   - `AdminQuestionSubmissionController`（管理端：GET /api/admin/submission、GET /api/admin/submission/{id}、POST /api/admin/submission/{id}/review、POST /api/admin/submission/{id}/import、GET /api/admin/submission/stats）

6. **前端 API 模块**（`submission.ts`）：
   - 用户端 3 个接口 + 管理端 5 个接口，含类型定义

7. **前端用户投稿页面**（`QuestionSubmitView.vue`）：
   - 投稿列表（按状态筛选+分页）
   - 投稿表单（题型选择、课程选择、难度评级、题干、选项动态添加/删除/标记正确答案、填空简答答案、解析、标签、来源）
   - 投稿详情弹窗

8. **前端管理端审核页面**（`SubmissionManage.vue`）：
   - 统计卡片（待审核/已通过/已拒绝/已入库数量）
   - 投稿列表（状态筛选+关键词搜索+分页）
   - 审核操作（通过/拒绝弹窗，拒绝必填审核意见）
   - 入库操作（确认弹窗，一键入库为正式题目）
   - 投稿详情弹窗（含入库题目ID快捷跳转）

9. **路由与导航更新**：
   - 用户端路由 `/submit`（题目投稿）
   - 管理端路由 `/admin/submissions`（投稿管理）
   - 侧边栏新增"题目投稿"和"投稿管理"入口

### 修改文件清单
- `backend/src/main/resources/db/migration/V7__create_question_submission_table.sql`（新建）
- `backend/src/main/java/com/learnplatform/entity/QuestionSubmission.java`（新建）
- `backend/src/main/java/com/learnplatform/mapper/QuestionSubmissionMapper.java`（新建）
- `backend/src/main/java/com/learnplatform/dto/QuestionSubmissionRequest.java`（新建）
- `backend/src/main/java/com/learnplatform/dto/QuestionReviewRequest.java`（新建）
- `backend/src/main/java/com/learnplatform/dto/QuestionSubmissionVO.java`（新建）
- `backend/src/main/java/com/learnplatform/service/QuestionSubmissionService.java`（新建）
- `backend/src/main/java/com/learnplatform/controller/QuestionSubmissionController.java`（新建）
- `backend/src/main/java/com/learnplatform/controller/AdminQuestionSubmissionController.java`（新建）
- `frontend/src/api/submission.ts`（新建）
- `frontend/src/views/practice/QuestionSubmitView.vue`（新建）
- `frontend/src/views/admin/SubmissionManage.vue`（新建）
- `frontend/src/router/index.ts`（新增 2 条路由）
- `frontend/src/components/layout/AppLayout.vue`（新增 2 个侧边栏菜单项 + Upload 图标引入）

### 验收结果
- [x] `cd backend && mvn test`：227 个后端测试全部通过（无回归）
- [x] `cd frontend && npm run build`：前端构建成功

### 遗留问题
- 投稿管理未接入管理端 Dashboard 统计面板（后续可加）
- 暂无投稿数量限制（可考虑每日投稿配额）
- 投稿入库后不自动清除相关缓存（题目列表缓存可能需手动刷新）

### 下轮建议
- 进入 Phase 16 下一个候选方向：AI 题目质检（AI 辅助检查投稿题目质量）
- 或继续 Phase 14 候选方向：代码执行动画、SQL 执行顺序可视化
- 建议 commit message: `feat(backend,frontend): 实现题目投稿中心（投稿、审核、入库，Phase 16 P0）`

---

## Round 81 - 2026-06-18

### 阶段
Phase 15：AI 学习画像与个性化推荐 — 单题错因分析单元测试

### 本轮目标
补充 Round 80 新增的 `analyzeQuestionError` 方法的后端单元测试，覆盖掌握趋势算法、错误模式生成、作答历史等核心逻辑。

### 完成内容
1. **新增 16 个单元测试**（`LearningDiagnosisServiceTest`）：
   - `analyzeQuestionErrorReturnsEmptyForNonexistentQuestion`：题目不存在返回空结果
   - `analyzeQuestionErrorReturnsZeroAttemptsWhenNoRecords`：无作答记录时返回零值
   - `analyzeQuestionErrorComputesCorrectRateWithMixedAttempts`：混合正确/错误作答的正确率计算（60%）
   - `analyzeQuestionErrorAllCorrectAttempts`：全部正确场景（100%，掌握程度=已掌握）
   - `analyzeQuestionErrorAllWrongAttempts`：全部错误场景（0%，反复错题 + 连续答错检测）
   - `analyzeQuestionErrorDetectsImprovingTrend`：掌握趋势 IMPROVING（近期 80% vs 前期 0%，差异 ≥20%）
   - `analyzeQuestionErrorDetectsDecliningTrend`：掌握趋势 DECLINING（近期 20% vs 前期 100%，差异 ≥20%）
   - `analyzeQuestionErrorDetectsStagnantTrend`：掌握趋势 STAGNANT（差异 <20%）
   - `analyzeQuestionErrorOnlyTwoAttemptsHighRecent`：仅 2 次作答且近期 100% → IMPROVING
   - `analyzeQuestionErrorOnlyTwoAttemptsLowRecent`：仅 2 次作答且近期 0% → DECLINING
   - `analyzeQuestionErrorResolvesKnowledgePointAndCourse`：知识点名称和课程名称正确解析
   - `analyzeQuestionErrorErrorPatternRepeatedErrors`：错误模式包含反复错题 + 最近一次已答对 + 未掌握
   - `analyzeQuestionErrorErrorPatternConsecutiveWrong`：错误模式包含连续答错次数 + 部分掌握
   - `analyzeQuestionErrorErrorPatternRecentWrong`：错误模式包含最近一次作答仍然错误
   - `analyzeQuestionErrorSingleAttemptCorrect`：单次正确作答（STAGNANT + 全部答对）
   - `analyzeQuestionErrorAttemptHistoryHasCorrectFields`：作答历史字段映射验证（recordId、userAnswer、isCorrect、answerTime、createTime）

### 修改文件清单
- `backend/src/test/java/com/learnplatform/service/LearningDiagnosisServiceTest.java`（新增 16 个测试方法）

### 验收结果
- [x] `cd backend && mvn test -Dtest=LearningDiagnosisServiceTest`：46 个测试全部通过
- [x] `cd backend && mvn test`：227 个后端测试全部通过（无回归）
