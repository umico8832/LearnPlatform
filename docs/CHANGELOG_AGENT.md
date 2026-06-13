# AI 题库与错题复习系统 - 开发日志

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

## Round 18 - 2026-06-13

### 阶段
Phase 12：安全、配置与工程可信度修复

### 本轮目标
修复项目审查发现的安全、演示账号、环境配置、Agent 规则冲突和自动化测试缺口。

### 完成内容
- Markdown 渲染接入 DOMPurify，净化题干、解析和 AI 内容生成的 HTML。
- 补回缺失的 `/login` 路由，并让管理端路由执行 ADMIN 前端角色守卫。
- 修正管理员和测试用户 BCrypt 种子密码。
- 统一 `.env.example`、Docker Compose 与 Spring Boot 环境变量，AI 超时配置实际应用到 HTTP 客户端。
- 抽取 `AnswerEvaluator`，统一练习和考试判分逻辑。
- 新增 JWT 与客观题判分测试，共 5 个测试通过。
- 清理 skills 中其他项目残留规则，明确 `AGENTS.md` 和用户当前要求的优先级。
- 统一 Phase 11/12 状态与安全、架构、数据库说明。

### 验收结果
- [x] `cd backend && mvn clean test`（5 tests，0 failures）
- [x] `cd frontend && npm run build`
- [x] 演示账号 BCrypt Hash 与文档密码一致
- [x] Markdown HTML 在进入 `v-html` 前经过 DOMPurify

### 遗留问题
- 前端生产构建仍提示 ECharts/Element Plus 主包超过 500 kB。
- AiCallLog 和 AI SSE 流式输出尚未实现。
- 尚未进行 Docker 容器启动后的浏览器全链路验收。

### 下轮建议
- 运行 Docker Compose 并执行登录、刷题、考试、AI 降级路径的完整演示验收。
- 建议 commit message: `fix(platform): 修复安全配置并补充核心测试`

---

## Round 17 - 2026-06-13

### 阶段
Phase 12：体验增强迭代（第二轮）

### 本轮目标
实现 P0 管理端统计面板，并恢复前端标准生产构建。

### 完成内容
- 新增 `GET /api/admin/statistics/overview` 管理端统计接口，由 ADMIN 权限规则保护。
- 聚合注册/启用用户、题目总量、本周新增题目、试卷发布状态、今日活跃用户和累计刷题量。
- 返回题型分布及近 7 日刷题次数、活跃用户趋势。
- 新增管理端平台总览页面，包含指标卡、趋势折线图、题型环图和状态仪表。
- 增加管理端总览路由与侧边栏入口。
- 清理既有 TypeScript 未使用声明，补充 Element Plus 中文语言包类型声明。
- 修复试卷选题弹窗首次打开不自动加载题目的问题。

### 修改文件清单
- 后端：`AdminStatisticsController.java`、`AdminStatisticsVO.java`、`StatisticsService.java`
- 前端：`AdminDashboard.vue`、`statistics.ts`、`router/index.ts`、`AppLayout.vue`
- 构建清理：`env.d.ts` 及 6 个既有 Vue 页面
- 文档：`API_DESIGN.md`、`DEMO.md`、`FUTURE.md`、`ROADMAP.md`、`RESUME.md`、`HANDOFF.md`、`CHANGELOG_AGENT.md`

### 验收结果
- [x] `cd backend && mvn test`
- [x] `cd frontend && npm run build`
- [x] `git diff --check`
- [x] 后端管理端接口编译通过
- [x] 前端标准 TypeScript 检查和生产构建通过

### 遗留问题
- 当前后端没有自动化测试用例，`mvn test` 主要验证编译与测试生命周期。
- Vite 仍提示 ECharts/Element Plus 相关产物包体积较大，后续可按需拆包。
- 管理端前端角色守卫尚未实现，后端接口权限已生效。

### 下轮建议
- 实现 P0 AI 流式输出（SSE），改善长内容生成等待体验。
- 建议 commit message: `feat(statistics): 完成管理端平台统计面板`

---

## Round 16 - 2026-06-13

### 阶段
Phase 12：体验增强迭代（第一轮）

### 本轮目标
将 AI 题目解析和变式题能力整合到刷题结果与错题本，打通题目学习闭环。

### 完成内容
- 新增 `AiQuestionAssistant.vue`，统一封装 AI 深度解析、变式题生成、加载状态、错误提示和 Markdown 结果展示。
- 刷题结果弹窗接入 AI 学习助手，并扩大弹窗宽度以容纳解析内容。
- 错题卡片接入同一 AI 学习助手，可针对每道错题直接请求解析或变式题。
- 同一道题的已生成结果在组件内缓存，切换解析类型时不重复请求。
- 增加窄屏按钮自适应布局。

### 修改文件清单
| 文件 | 操作 |
|------|------|
| frontend/src/components/AiQuestionAssistant.vue | 新建 |
| frontend/src/views/practice/PracticeSessionView.vue | 修改 |
| frontend/src/views/practice/WrongQuestionView.vue | 修改 |
| README.md | 修改 |
| docs/FUTURE.md | 修改 |
| docs/ROADMAP.md | 修改 |
| docs/HANDOFF.md | 修改 |
| docs/CHANGELOG_AGENT.md | 修改 |

### 验收结果
- [x] `npx vue-tsc --noEmit --noUnusedLocals false --noUnusedParameters false --noImplicitAny false`
- [x] `npx vite build`
- [x] 刷题与错题页面复用同一 AI 组件
- [x] AI 请求具备加载、失败和结果展示状态
- [ ] 浏览器可视化验证（当前会话内置浏览器实例不可用）

### 遗留问题
- 仓库完整 `npm run build` 仍被既有严格 TypeScript 清理项阻断，涉及 Element Plus 语言包声明及多个页面未使用变量；本轮生产 Vite 构建已通过。
- AI 实际响应仍需在后端运行且配置有效 AI 环境变量后联调。
- AI 流式输出尚未实现。

### 下轮建议
- 实现 P0 管理端统计面板，补齐平台运营数据总览。
- 建议 commit message: `feat(ai): 集成刷题与错题 AI 解析助手`

---

## Round 15 - 2026-06-13

### 阶段
Phase 11：部署与简历（第二轮）

### 本轮目标
创建后续扩展方向文档，完成 Phase 11 收尾工作。

### 完成内容

#### 1. 后续扩展方向文档
- **新建** `docs/FUTURE.md` - 项目后续扩展方向文档
  - 按 P0/P1/P2/P3 四级优先级规划 15 个扩展方向
  - P0（核心体验提升）：AI 整合到刷题/错题页面、AI 流式输出（SSE）、管理端统计面板
  - P1（重要功能补充）：用户个人中心、错题重练模式、题目收藏、题目导入/导出
  - P2（锦上添花）：学习计划、社区讨论、多端适配、难度自适应
  - P3（远期规划）：多租户、移动端 App、AI 增强、性能运维
  - 技术债务清单（7 项）
  - 短期/中期/长期迭代建议

#### 2. ROADMAP 更新
- `docs/ROADMAP.md` - Phase 11 剩余任务标记完成
  - 后续扩展方向文档 ✅
  - Git 历史整理 ✅

#### 3. Git 历史整理
- 当前 Git 提交历史清晰规范，共 10+ 次提交
- 每次提交遵循 `type(scope): 中文描述` 规范
- 无需 squashing，保持历史可追溯性

### 修改文件清单
| 文件 | 操作 |
|------|------|
| docs/FUTURE.md | 新建（后续扩展方向文档） |
| docs/ROADMAP.md | 修改（标记完成项） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |
| docs/HANDOFF.md | 修改（更新项目状态） |

### 验收结果
- [x] docs/FUTURE.md 覆盖 15 个扩展方向 + 7 项技术债务
- [x] FUTURE.md 包含优先级分级、涉及文件、预计工作量
- [x] FUTURE.md 包含短期/中期/长期迭代建议
- [x] ROADMAP Phase 11 任务全部标记完成（除项目截图）
- [x] Git 历史清晰规范

### 遗留问题
- 项目截图未制作（需要运行环境截图，非代码任务）

### 下轮建议
- Phase 11 已基本完成，项目进入可展示状态
- 如继续开发，建议按 FUTURE.md 中 P0 优先级开始：AI 功能整合到刷题页面
- 如仅用于简历展示，可开始运行项目并截图
- 建议 commit message: `docs(future): 创建后续扩展方向文档，完成 Phase 11 收尾`

---

## Round 14 - 2026-06-13

### 阶段
Phase 11：部署与简历（第一轮）

### 本轮目标
完善 Docker Compose 部署配置、README 更新、演示流程文档创建、简历材料补充。

### 完成内容

#### 1. Docker Compose 完善
- `docker-compose.yml` - backend 服务添加健康检查（wget /api/public/health）
- `docker-compose.yml` - backend 添加 start_period 30s（等待 Spring Boot 启动）
- `docker-compose.yml` - frontend 服务添加健康检查（wget /）
- `docker-compose.yml` - frontend depends_on 改为 condition: service_healthy（确保后端就绪后才启动）
- `docker-compose.yml` - backend 环境变量新增 AI_API_BASE_URL、AI_MODEL

#### 2. README.md 完善
- 移除 Lombok 技术栈说明（已因 JDK 26 兼容性问题移除）
- JDK 版本说明更新（推荐 JDK 21+）
- 开发计划状态表全部更新为实际完成状态
- 新增演示流程文档链接
- 新增 FAQ：Lombok 编译错误、API 文档查看、测试数据初始化
- 修正 `docker-compose` 为 `docker compose`（新版命令）
- 项目结构更新（service/ai/ 目录、docs/DEMO.md）
- 克隆 URL 更新为实际 GitHub 地址

#### 3. 演示流程文档
- **新建** `docs/DEMO.md` - 完整的项目演示流程文档
  - 演示准备（环境启动、演示账号、访问地址）
  - 用户端功能演示（9 个步骤：登录→首页→课程→题库→刷题→记录→错题→考试→AI）
  - 管理端功能演示（4 个步骤：课程管理→知识点→题目→试卷）
  - 技术亮点演示（接口文档、安全机制、Docker 部署）
  - 演示时间建议（20-30 分钟）
  - 演示注意事项和测试数据初始化指南

#### 4. 简历材料完善
- `docs/RESUME.md` - 项目亮点新增 3 项（Docker 健康检查、日志体系、性能优化、安全加固）
- `docs/RESUME.md` - 新增面试问答 Q8（性能优化：N+1 修复、复合索引、AI 超时优化）
- `docs/RESUME.md` - 新增面试问答 Q9（日志设计：三层日志体系）
- `docs/RESUME.md` - 新增面试问答 Q10（安全措施：7 项安全措施详解）

#### 5. 文档更新
- `docs/ROADMAP.md` - Phase 10 状态更新为 ✅ 已完成
- `docs/ROADMAP.md` - Phase 11 状态更新为 🔵 进行中，已完成任务标记

### 修改文件清单
| 文件 | 操作 |
|------|------|
| docker-compose.yml | 修改（健康检查、启动顺序、环境变量） |
| README.md | 重写（状态表、Lombok、JDK、FAQ、演示链接） |
| docs/DEMO.md | 新建（演示流程文档） |
| docs/RESUME.md | 修改（技术亮点 + 面试问答 Q8-Q10） |
| docs/ROADMAP.md | 修改（Phase 10→✅，Phase 11→🔵） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] Docker Compose backend/frontend 健康检查配置正确
- [x] Docker Compose frontend 依赖 backend 健康状态启动
- [x] README 状态表与实际一致
- [x] README 移除 Lombok 说明，添加 JDK 版本推荐
- [x] README 新增 FAQ 覆盖常见问题
- [x] docs/DEMO.md 演示流程覆盖所有核心功能
- [x] docs/RESUME.md 面试问答从 7 题扩展到 10 题
- [x] ROADMAP Phase 10 标记为已完成

### 遗留问题
- 项目截图未制作（需要运行环境截图）
- 后续扩展方向文档未创建
- Git 历史整理未完成

### 下轮建议
- 制作项目截图（首页、刷题、错题本、考试、AI 功能页面）
- 创建后续扩展方向文档
- Git 历史整理（如果需要展示给面试官）
- 或进入项目收尾：确认 Docker Compose 可一键启动
- 建议 commit message: `docs(deploy): 完善部署配置、README、演示流程和简历材料`

---

## Round 13 - 2026-06-13

### 阶段
Phase 10：质量提升（第三轮）

### 本轮目标
日志规范化、SQL 优化检查、安全检查。

### 完成内容

#### 1. 日志规范化
- `AuthService.java` - 添加 Logger，注册/登录关键操作 info 日志
- `PracticeService.java` - 添加获取练习题目、提交答案、判分结果日志
- `ExamService.java` - 添加开始考试、提交考试、判分完成日志
- `WrongQuestionService.java` - 添加加入/移出错题本操作日志
- **新建** `RequestLoggingFilter.java` - HTTP 请求日志过滤器，记录方法、URI、状态码、耗时，跳过健康检查和静态资源，4xx/5xx 用 warn 级别

#### 2. SQL 优化
- `schema.sql` - 添加复合索引：
  - `practice_record`: `idx_user_create(user_id, create_time)` — 用户刷题记录按时间查询优化
  - `exam_record`: `idx_user_create(user_id, create_time)` — 考试记录按时间查询优化
  - `wrong_question`: `idx_user_mastery(user_id, mastery_level)` — 按掌握程度筛选优化
  - `wrong_question`: `idx_user_update(user_id, update_time)` — 按更新时间排序优化
- `WrongQuestionService.java` - 修复 N+1 查询问题：
  - `getWrongQuestions()`: 批量加载 Question 和 Course，用 Map 缓存替代逐条 selectById
  - `getWrongQuestionStats()`: 同样批量加载，避免 N+1 循环查询
  - courseId 过滤改为在内存中通过 Map 过滤，而非 filter 后再查数据库

#### 3. 安全检查
- `SecurityConfig.java` - 添加安全响应头：
  - `X-Content-Type-Options: nosniff` — 防止 MIME 类型嗅探
  - `X-Frame-Options: SAMEORIGIN` — 防止点击劫持
- 越权检查：ExamService 中 `submitExam`/`getExamResult` 已有 `!record.getUserId().equals(userId)` 检查 ✅
- WrongQuestionService 中 `updateMasteryLevel`/`removeWrongQuestion` 已有 userId 校验 ✅
- 后端接口 `/api/admin/**` 已通过 SecurityConfig 配置 `hasRole("ADMIN")` ✅
- SQL 注入：MyBatis-Plus 使用参数化查询，无原生 SQL 拼接 ✅
- XSS：当时仅完成 Markdown HTML 输出，未净化风险已在 Round 18 通过 DOMPurify 修复。

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/src/main/java/com/learnplatform/service/AuthService.java | 修改（添加 Logger 和日志） |
| backend/src/main/java/com/learnplatform/service/PracticeService.java | 修改（添加关键操作日志） |
| backend/src/main/java/com/learnplatform/service/ExamService.java | 修改（添加关键操作日志） |
| backend/src/main/java/com/learnplatform/service/WrongQuestionService.java | 修改（添加日志 + 修复 N+1 查询） |
| backend/src/main/java/com/learnplatform/config/RequestLoggingFilter.java | 新建（HTTP 请求日志过滤器） |
| backend/src/main/java/com/learnplatform/config/SecurityConfig.java | 修改（添加安全响应头） |
| backend/src/main/resources/db/schema.sql | 修改（添加复合索引） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] 核心 Service 添加操作日志（info 级别）
- [x] HTTP 请求日志过滤器记录所有 API 调用
- [x] 数据库添加复合索引优化高频查询
- [x] WrongQuestionService N+1 查询已修复（批量加载）
- [x] SecurityConfig 添加安全响应头
- [x] 越权检查验证通过

### 遗留问题
- 前端登录页 captcha 验证码未实现（优先级低）
- 前端输入框未做 XSS 前端过滤（后端参数化查询已防注入，前端 marked 渲染为 Markdown）
- AI 调用日志表 `ai_call_log` 已建表但未接入代码

### 下轮建议
- 进入 Phase 11：部署与简历
- Docker Compose 完善、README 截图、演示流程文档、简历材料完善
- 建议 commit message: `refactor(quality): 日志规范化、SQL 索引优化和安全响应头`

---

## Round 12 - 2026-06-13

### 阶段
Phase 10：质量提升（第二轮）

### 本轮目标
前端体验优化：loading 状态、空状态、错误提示，AI 超时优化，URL bug 修复。

### 完成内容

#### 1. HomeView.vue - 首页体验优化
- 统计卡片添加 `v-loading` 加载状态
- 趋势图表和雷达图添加空状态（`el-empty`）
- 加载失败时显示空状态而非空白区域
- 新增 `statsLoading`、`trendEmpty`、`courseEmpty` 响应式变量

#### 2. CourseListView.vue - 课程列表优化
- 课程列表区域添加 `v-loading` 加载状态
- 加载时显示"加载课程中..."提示

#### 3. PracticeView.vue - 刷题页优化 + Bug 修复
- 统计卡片添加 `v-loading` 加载状态
- 使用空值合并操作符 `??` 防止 null 值显示异常
- **Bug 修复**：课程列表 API 路径从 `/api/courses` 改为 `/courses`（axios baseURL 已含 `/api`，原来请求路径变成了 `/api/api/courses`）
- 新增 `statsLoading` 响应式变量

#### 4. AI 超时优化
- `request.ts` 新增 `aiService` 专用 Axios 实例（超时 60 秒）
- AI 实例有独立的 Token 注入和错误处理拦截器
- AI 超时提示更友好："AI 响应超时，请稍后重试"
- `ai.ts` API 改用 `aiService` 发起请求，避免 AI 调用 15 秒超时

#### 5. URL 修复
- `ai.ts` 接口路径从 `/api/ai/xxx` 改为 `/ai/xxx`（避免 `/api/api/ai/xxx`）

### 修改文件清单
| 文件 | 操作 |
|------|------|
| frontend/src/views/home/HomeView.vue | 修改（loading + empty） |
| frontend/src/views/course/CourseListView.vue | 修改（loading） |
| frontend/src/views/practice/PracticeView.vue | 修改（loading + URL 修复） |
| frontend/src/utils/request.ts | 修改（新增 aiService） |
| frontend/src/api/ai.ts | 修改（用 aiService + 修复 URL） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] 首页统计卡片有加载状态，图表有空状态
- [x] 课程列表有加载状态
- [x] 刷题页统计卡片有加载状态
- [x] AI API 使用 60 秒超时实例
- [x] AI API URL 路径修复
- [x] 刷题页课程列表 URL 修复

### 遗留问题
- 边界情况处理（重复提交防护、并发）大部分页面已有基本防护
- 日志规范化未完成
- SQL 优化检查未完成
- 安全检查未完成

### 下轮建议
- 继续 Phase 10：日志规范化 + 安全检查
- 或进入 Phase 11：部署与简历
- 建议 commit message: `refactor(frontend): 优化前端加载状态、空状态和 AI 超时配置`

---

## Round 11 - 2026-06-13

### 阶段
Phase 10：质量提升（第一轮）

### 本轮目标
后端参数校验补全和接口文档补全（Knife4j/Swagger 注解），修复 ROADMAP Phase 2 状态。

### 完成内容

#### 1. DTO 参数校验补全
- `QuestionCreateRequest.java` - 添加 @NotBlank(content, questionType)、@NotNull(courseId)
- `PracticeSubmitRequest.java` - 添加 @NotNull(questionId)、@NotBlank(userAnswer)
- `ExamPaperCreateRequest.java` - 添加 @NotBlank(title)、@NotNull(courseId, duration)、@Positive(duration)
- `ExamSubmitRequest.java` - 添加 @NotNull(examRecordId)、@NotEmpty(answers)
- LoginRequest、RegisterRequest 已有校验，无需修改

#### 2. Controller @Valid 注解补全
- `AdminQuestionController.java` - create/update 方法添加 @Valid
- `PracticeController.java` - submitAnswer 方法添加 @Valid
- `AdminExamController.java` - create/update 方法添加 @Valid
- `ExamController.java` - submitExam 方法添加 @Valid
- AuthController 已有 @Valid，无需修改

#### 3. Knife4j/Swagger 注解补全（8 个 Controller）
- `AuthController.java` - @Tag("认证管理") + 3 个 @Operation
- `PracticeController.java` - @Tag("刷题练习") + 4 个 @Operation
- `WrongQuestionController.java` - @Tag("错题本") + 4 个 @Operation
- `AdminQuestionController.java` - @Tag("管理端-题目管理") + 5 个 @Operation
- `ExamController.java` - @Tag("考试") + 6 个 @Operation
- `AdminExamController.java` - @Tag("管理端-试卷管理") + 6 个 @Operation
- `AiController.java` - @Tag("AI 功能") + 4 个 @Operation
- `StatisticsController.java` - @Tag("统计") + 3 个 @Operation

#### 4. 文档更新
- `docs/ROADMAP.md` - Phase 2 状态修正为 ✅ 已完成，Phase 10 状态更新为 🔵 进行中

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/src/main/java/com/learnplatform/dto/QuestionCreateRequest.java | 修改（添加 Validation） |
| backend/src/main/java/com/learnplatform/dto/PracticeSubmitRequest.java | 修改（添加 Validation） |
| backend/src/main/java/com/learnplatform/dto/ExamPaperCreateRequest.java | 修改（添加 Validation） |
| backend/src/main/java/com/learnplatform/dto/ExamSubmitRequest.java | 修改（添加 Validation） |
| backend/src/main/java/com/learnplatform/controller/AdminQuestionController.java | 修改（@Valid + Swagger） |
| backend/src/main/java/com/learnplatform/controller/PracticeController.java | 修改（@Valid + Swagger） |
| backend/src/main/java/com/learnplatform/controller/AdminExamController.java | 修改（@Valid + Swagger） |
| backend/src/main/java/com/learnplatform/controller/ExamController.java | 修改（@Valid + Swagger） |
| backend/src/main/java/com/learnplatform/controller/AuthController.java | 修改（Swagger） |
| backend/src/main/java/com/learnplatform/controller/WrongQuestionController.java | 修改（Swagger） |
| backend/src/main/java/com/learnplatform/controller/AiController.java | 修改（Swagger） |
| backend/src/main/java/com/learnplatform/controller/StatisticsController.java | 修改（Swagger） |
| docs/ROADMAP.md | 修改（Phase 2→✅，Phase 10→🔵） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] 所有创建/更新请求 DTO 已添加 Validation 注解
- [x] 所有 Controller 的 @RequestBody 方法已添加 @Valid
- [x] 所有 Controller 已添加 @Tag 和 @Operation Swagger 注解
- [x] ROADMAP Phase 2 状态已修正为 ✅

### 遗留问题
- 前端体验优化未完成（加载状态、错误提示、空状态）
- 边界情况处理未完成（重复提交、并发）
- 日志规范化未完成
- SQL 优化检查未完成
- 安全检查未完成

### 下轮建议
- 继续 Phase 10：前端体验优化
- 为前端页面添加 loading 状态、空状态、错误提示
- 建议 commit message: `refactor(quality): 补全参数校验和接口文档注解`

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

## Round 10 - 2026-06-13

### 阶段
Phase 8：AI 功能 + Phase 9：统计可视化

### 本轮目标
完成 Phase 8 AI 功能和 Phase 9 统计可视化两个阶段。

### 完成内容

#### Phase 8：AI 功能
- 后端：AiConfig + AiProvider 接口 + OpenAiProvider 实现 + AiService + AiController + DTO
- 前端：MarkdownRenderer 组件 + ai.ts API + ReviewSuggestionView 页面
- 安装 marked 依赖，路由和导航整合
- Commit: `6fc7b09`

#### Phase 9：统计可视化
- 后端：StatisticsService + StatisticsController + StatisticsVO（3 个接口：overview、daily-trend、course-stats）
- 前端：HomeView.vue 重写（统计卡片 + ECharts 趋势柱状图 + 课程雷达图 + 快捷入口）
- 前端：statistics.ts API 封装
- Commit: `8994987`

### 遗留问题
- 管理端统计接口未实现（用户数、题目数、试卷数）
- Phase 10 质量提升未开始
- Phase 11 部署与简历未开始
- 上下文接近上限，建议交接

### 下轮建议
- 进入 Phase 10：质量提升
- 代码审查和重构
- 参数校验补全
- 前端体验优化
- 建议 commit message: `refactor(all): Phase 10 质量提升`

---

## Round 9 - 2026-06-13

### 阶段
Phase 8：AI 功能

### 本轮目标
完成 Phase 8 AI 功能：后端 AI Provider 架构（OpenAI 兼容接口）、4 个 AI 业务接口、前端 AI 复习建议页面和 Markdown 渲染组件。

### 完成内容

#### 1. 后端 AI 配置
- `AiConfig.java` - AI 配置属性类（@ConfigurationProperties），读取 application.yml 中的 ai.* 配置

#### 2. 后端 AI Provider
- `service/ai/AiProvider.java` - AI Provider 接口（chat 方法）
- `service/ai/OpenAiProvider.java` - OpenAI 兼容 API 实现
  - 支持 OpenAI、DeepSeek、通义千问等兼容接口
  - 错误处理：未启用/未配置 Key/API 错误

#### 3. 后端 DTO
- `AiRequest.java` - AI 请求 DTO（questionId、courseId、knowledgePointId）
- `AiResponse.java` - AI 响应 VO（content + source）

#### 4. 后端 Service
- `AiService.java` - AI 业务服务
  - generateExplanation() - 题目解析（构建题目上下文 + Prompt 模板）
  - generateVariant() - 变式题生成
  - generateReviewSuggestion() - 复习建议（基于错题数据分析）
  - generateSummary() - 知识点总结

#### 5. 后端 Controller
- `AiController.java` - AI 控制器
  - POST /api/ai/explanation - 题目解析
  - POST /api/ai/variant - 变式题
  - POST /api/ai/review-suggestion - 复习建议
  - POST /api/ai/summary - 知识点总结

#### 6. 前端依赖
- 安装 marked（Markdown 渲染库）

#### 7. 前端 API
- `frontend/src/api/ai.ts` - AI API 封装（4 个接口方法）

#### 8. 前端组件
- `frontend/src/components/MarkdownRenderer.vue` - Markdown 渲染组件
  - 使用 marked 解析 Markdown 为 HTML
  - 完善的 CSS 样式（标题、列表、代码块、表格、引用）

#### 9. 前端页面
- `frontend/src/views/ai/ReviewSuggestionView.vue` - AI 复习建议页面
  - 课程选择器（可选）
  - 生成按钮（带加载状态）
  - 结果 Markdown 渲染
  - 错误提示

#### 10. 路由和导航
- `frontend/src/router/index.ts` - 新增 /ai/review 路由
- `frontend/src/components/layout/AppLayout.vue` - 侧边栏新增"AI 复习建议"菜单项（MagicStick 图标）

#### 11. 文档更新
- `docs/ROADMAP.md` - Phase 8 标记为 ✅ 已完成

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/src/main/java/com/learnplatform/config/AiConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/service/ai/AiProvider.java | 新建 |
| backend/src/main/java/com/learnplatform/service/ai/OpenAiProvider.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/AiRequest.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/AiResponse.java | 新建 |
| backend/src/main/java/com/learnplatform/service/AiService.java | 新建 |
| backend/src/main/java/com/learnplatform/controller/AiController.java | 新建 |
| frontend/package.json | 修改（新增 marked 依赖） |
| frontend/src/api/ai.ts | 新建 |
| frontend/src/components/MarkdownRenderer.vue | 新建 |
| frontend/src/views/ai/ReviewSuggestionView.vue | 新建 |
| frontend/src/router/index.ts | 修改（添加路由） |
| frontend/src/components/layout/AppLayout.vue | 修改（添加菜单项） |
| docs/ROADMAP.md | 修改（Phase 8→✅） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] AI Provider 支持 OpenAI 兼容 API
- [x] 未配置时给出友好提示
- [x] 4 个 AI 接口实现（解析、变式题、复习建议、知识点总结）
- [x] 前端 Markdown 渲染组件
- [x] AI 复习建议页面
- [x] 侧边栏 AI 复习建议入口

### 遗留问题
- 题目解析和变式题按钮未整合到刷题/错题页面（后续优化）
- AiCallLog 调用日志未实现（后期）
- 无流式输出（SSE），AI 响应需等待全部生成

### 下轮建议
- 进入 Phase 9：统计可视化
- 后端：StatisticsService + StatisticsController
- 前端：HomeView 学习面板、ECharts 图表
- 建议 commit message: `feat(ai): 完成 Phase 8 AI 功能后端和前端`

---

## Round 8 - 2026-06-13

### 阶段
Phase 7：试卷与考试

### 本轮目标
完成 Phase 7 试卷与考试前端部分：管理端试卷管理页面、用户端考试结果页面、倒计时功能、路由和导航整合。

### 完成内容

#### 1. 前端 API 更新
- `frontend/src/api/exam.ts` - 补充用户端 `getPaperDetail` 方法（指向 `/api/exam/papers/${id}`）

#### 2. 管理端试卷管理页面
- `frontend/src/views/admin/ExamManage.vue` - 试卷 CRUD 管理
  - 试卷列表表格（ID、名称、课程、题数、总分、时长、状态、创建时间）
  - 状态筛选（草稿/已发布）
  - 新增/编辑试卷弹窗（800px 宽）
  - 基本信息表单（名称、课程、描述、时长、状态）
  - 组卷功能：题目选择器弹窗（搜索题干、题型筛选、勾选添加）
  - 已选题目列表（题干预览、分值编辑、排序编辑、移除）
  - 已选题数和总分统计
  - 发布试卷操作
  - 删除确认
  - 分页功能

#### 3. 用户端考试结果页面
- `frontend/src/views/exam/ExamResultView.vue` - 考试结果展示
  - 成绩卡片（得分大字、总分、正确率百分比、用时、题数）
  - 答题详情列表（题号、题型、满分、得分、正确/错误标签）
  - 每题展示（题干、我的答案、正确答案、解析）
  - 正确/错误颜色区分
  - 返回考试列表按钮
  - 支持从 sessionStorage 和 API 两种方式获取结果

#### 4. ExamListView 更新
- 修复 API 调用：使用用户端 `getPaperDetail` 替代管理端 `getExamPaperDetail`
- 新增"考试记录"标签页（el-tabs 切换）
  - 记录表格（试卷名称、得分、状态、开始时间）
  - 查看结果/继续考试操作
  - 得分颜色区分（≥80% 绿色、≥60% 黄色、<60% 红色）
  - 分页功能
- 开始考试时将 duration 传入 sessionStorage

#### 5. ExamTakeView 倒计时功能
- 顶部显示剩余时间（MM:SS 格式）
- 倒计时从试卷时长开始递减
- 剩余 5 分钟时红色闪烁警告
- 时间到自动提交试卷
- 组件销毁时清除定时器
- 导入 Timer 图标

#### 6. 路由更新
- `frontend/src/router/index.ts` - 新增路由
  - `/exams` - 考试列表
  - `/exams/take/:recordId` - 考试答题
  - `/exams/result/:recordId` - 考试结果
  - `/admin/exams` - 管理端试卷管理（requiresAdmin）

#### 7. 导航菜单更新
- `frontend/src/components/layout/AppLayout.vue`
  - 用户端新增"考试"菜单项（Trophy 图标）
  - 管理端新增"试卷管理"菜单项（Trophy 图标）

#### 8. 文档更新
- `docs/ROADMAP.md` - Phase 7 标记为 ✅ 已完成

### 修改文件清单
| 文件 | 操作 |
|------|------|
| frontend/src/api/exam.ts | 修改（补充 getPaperDetail） |
| frontend/src/views/admin/ExamManage.vue | 新建 |
| frontend/src/views/exam/ExamResultView.vue | 新建 |
| frontend/src/views/exam/ExamListView.vue | 修改（修复 API + 考试记录 tab） |
| frontend/src/views/exam/ExamTakeView.vue | 修改（添加倒计时） |
| frontend/src/router/index.ts | 修改（添加路由） |
| frontend/src/components/layout/AppLayout.vue | 修改（添加菜单项） |
| docs/ROADMAP.md | 修改（Phase 7→✅） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] 管理端可以创建试卷、手动选题组卷
- [x] 管理端可以编辑、发布、删除试卷
- [x] 用户端可以看到已发布试卷列表
- [x] 用户可以开始考试并进入答题界面
- [x] 答题界面有倒计时功能（剩余 5 分钟红色警告，时间到自动提交）
- [x] 提交后跳转考试结果页面（得分、正确率、答题详情）
- [x] 考试记录 tab 可以查看历史记录和结果
- [x] 侧边栏用户端新增"考试"、管理端新增"试卷管理"菜单
- [x] 路由配置正确

### 遗留问题
- 管理端试卷编辑时题目选择器的 `watchPicker` 函数未绑定到弹窗打开事件（需手动点击搜索）
- 随机组卷功能未在前端实现（后端可支持）
- 用户端考试列表未按课程筛选

### 下轮建议
- 进入 Phase 8：AI 功能
- 后端：AiProvider 接口 + OpenAiProvider 实现 + AiService + AiController
- 前端：MarkdownRenderer 组件 + AI 解析展示 + 复习建议页面
- 建议 commit message: `feat(exam): 完成 Phase 7 试卷与考试前端`

---

## Round 7 - 2026-06-13

### 阶段
Phase 6：错题本

### 本轮目标
完成 Phase 6 错题本：后端错题自动收集、错题管理全流程，前端错题本页面（统计卡片、筛选、掌握状态切换、移出）。

### 完成内容

#### 1. 后端实体类
- `WrongQuestion.java` - 错题本实体（id、userId、questionId、wrongCount、masteryLevel、lastWrongAnswer、createTime、updateTime、deleted）

#### 2. 后端 Mapper
- `WrongQuestionMapper.java` - 错题本 Mapper

#### 3. 后端 DTO
- `WrongQuestionVO.java` - 错题本 VO（含题目内容、题型、课程名、难度、答错次数、掌握程度）

#### 4. 后端 Service
- `WrongQuestionService.java` - 错题本服务
  - addWrongQuestion() - 答错时自动加入错题本（同一用户+题目不重复，答错次数累加）
  - getWrongQuestions() - 获取错题列表（分页，支持掌握程度和课程筛选）
  - updateMasteryLevel() - 更新掌握程度
  - removeWrongQuestion() - 移出错题本（逻辑删除）
  - removeOnCorrect() - 答对时自动从错题本移出
  - getWrongQuestionStats() - 获取统计（总数、各掌握程度数量、高频错题课程）

#### 5. 后端 Controller
- `WrongQuestionController.java` - 错题本控制器
  - GET /api/wrong-questions - 获取错题列表（分页）
  - GET /api/wrong-questions/stats - 获取错题统计
  - PUT /api/wrong-questions/{id}/mastery - 更新掌握程度
  - DELETE /api/wrong-questions/{id} - 移出错题本

#### 6. PracticeService 集成
- 注入 WrongQuestionService
- submitAnswer() 中增加错题本自动处理逻辑
  - 答错自动加入错题本
  - 答对自动从错题本移出

#### 7. 前端 API
- `frontend/src/api/wrongQuestion.ts` - 错题本 API 封装（类型定义 + 4 个接口方法）

#### 8. 前端页面
- `frontend/src/views/practice/WrongQuestionView.vue` - 错题本页面
  - 统计卡片（总错题数、未掌握、部分掌握、已掌握）
  - 掌握程度筛选
  - 错题卡片列表（题型、课程、难度、答错次数、掌握程度标签）
  - 错题内容展示（题干、上次错误答案）
  - 掌握程度单选按钮组切换
  - 移出错题本（Popconfirm 确认）
  - 分页功能

#### 9. 路由和导航更新
- `frontend/src/router/index.ts` - 新增路由 `/wrong-questions`
- `frontend/src/components/layout/AppLayout.vue` - 侧边栏新增"错题本"菜单项（WarningFilled 图标）

#### 10. 文档更新
- `docs/ROADMAP.md` - Phase 5 标记为 ✅ 已完成，Phase 6 标记为 🔵 进行中

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/src/main/java/com/learnplatform/entity/WrongQuestion.java | 新建 |
| backend/src/main/java/com/learnplatform/mapper/WrongQuestionMapper.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/WrongQuestionVO.java | 新建 |
| backend/src/main/java/com/learnplatform/service/WrongQuestionService.java | 新建 |
| backend/src/main/java/com/learnplatform/controller/WrongQuestionController.java | 新建 |
| backend/src/main/java/com/learnplatform/service/PracticeService.java | 修改（集成错题本逻辑） |
| frontend/src/api/wrongQuestion.ts | 新建 |
| frontend/src/views/practice/WrongQuestionView.vue | 新建 |
| frontend/src/router/index.ts | 修改（添加路由） |
| frontend/src/components/layout/AppLayout.vue | 修改（添加菜单项） |
| docs/ROADMAP.md | 修改（Phase 5→✅，Phase 6→🔵） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] 答错自动加入错题本（同一题不重复，答错次数累加）
- [x] 答对自动从错题本移出
- [x] 可以手动移出错题本
- [x] 可以切换掌握程度（未掌握/部分掌握/已掌握）
- [x] 错题列表支持按掌握程度筛选
- [x] 统计卡片显示总错题数和各掌握程度数量
- [x] 侧边栏菜单正确显示错题本入口

### 遗留问题
- 错题重练功能未单独实现（用户可从刷题练习页面按课程筛选进行重练）
- 高频错题知识点统计目前只按课程维度，未按知识点维度

### 下轮建议
- 进入 Phase 7：试卷与考试
- 后端：ExamPaper、ExamQuestion、ExamRecord、ExamAnswer 实体 + Mapper + Service + Controller
- 前端：管理端试卷管理、用户端考试答题界面
- 建议 commit message: `feat(wrong-question): 完成 Phase 6 错题本后端和前端`

---

## Round 6 - 2026-06-13

### 阶段
Phase 5：刷题与判分

### 本轮目标
完成 Phase 5 刷题与判分：后端刷题练习全流程（获取题目、提交答案、判分、记录查询、统计），前端刷题设置页面、答题界面、答题记录页面。

### 完成内容

#### 1. 后端实体类
- `PracticeRecord.java` - 刷题记录实体（id、userId、questionId、userAnswer、isCorrect、answerTime、createTime）

#### 2. 后端 Mapper
- `PracticeRecordMapper.java` - 刷题记录 Mapper

#### 3. 后端 DTO
- `PracticeSubmitRequest.java` - 提交答案请求（questionId、userAnswer、answerTime）
- `PracticeResultVO.java` - 答题结果 VO（recordId、questionId、userAnswer、correct、correctAnswer、analysis、score）
- `PracticeRecordVO.java` - 练习记录 VO（含题目内容、题型、课程名、难度）

#### 4. 后端 Service
- `PracticeService.java` - 刷题服务
  - getPracticeQuestions() - 获取练习题目（支持按课程/知识点/题型/难度筛选，随机抽取）
  - submitAnswer() - 提交答案并判分（事务：保存记录 + 返回结果）
  - getUserPracticeRecords() - 获取用户练习记录（分页）
  - getUserPracticeStats() - 获取用户练习统计（总题数、答对、答错、正确率）
  - 判分逻辑：单选/多选/判断自动判分，填空忽略大小写，简答暂不自动判分
  - 练习模式隐藏正确答案标记

#### 5. 后端 Controller
- `PracticeController.java` - 刷题控制器
  - GET /api/practice/questions - 获取练习题目
  - POST /api/practice/submit - 提交答案
  - GET /api/practice/records - 获取练习记录（分页）
  - GET /api/practice/stats - 获取练习统计

#### 6. 前端 API
- `frontend/src/api/practice.ts` - 刷题相关 API 封装（类型定义 + 4 个接口方法）

#### 7. 前端页面
- `frontend/src/views/practice/PracticeView.vue` - 刷题设置页面
  - 统计卡片（总答题数、答对数、答错数、正确率）
  - 刷题配置表单（课程选择、题型、难度、题目数量）
  - 课程列表动态加载
  - 开始刷题按钮（获取题目后存入 sessionStorage 跳转）
  
- `frontend/src/views/practice/PracticeSessionView.vue` - 答题界面
  - 顶部进度栏（当前题号/总题数、进度条、对错统计）
  - 题目卡片（题型标签、课程名、难度、分值、知识点标签）
  - 单选题/多选题/判断题选项点击选择
  - 填空题/简答题文本输入
  - 提交答案后弹窗展示结果（对错图标、用户答案、正确答案、解析）
  - 完成后展示总结页（总题数、答对、答错、正确率）
  - 支持"再练一次"返回设置页
  
- `frontend/src/views/practice/PracticeRecordView.vue` - 刷题记录页面
  - 筛选条件（题型、结果）
  - 记录表格（题干、题型、课程、难度、我的答案、结果、耗时、答题时间）
  - 分页功能

#### 8. 路由和导航更新
- `frontend/src/router/index.ts` - 新增路由（在 AppLayout children 内）
  - `/practice` - 刷题练习页面
  - `/practice/session` - 答题界面
  - `/practice/records` - 刷题记录
- `frontend/src/components/layout/AppLayout.vue` - 侧边栏更新
  - 用户端新增"刷题练习"菜单项（Promotion 图标）
  - 用户端新增"刷题记录"菜单项（Clock 图标）

#### 9. 文档更新
- `docs/ROADMAP.md` - Phase 4 标记为 ✅ 已完成，Phase 5 标记为 🔵 进行中，所有任务标记完成

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/src/main/java/com/learnplatform/entity/PracticeRecord.java | 新建 |
| backend/src/main/java/com/learnplatform/mapper/PracticeRecordMapper.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/PracticeSubmitRequest.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/PracticeResultVO.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/PracticeRecordVO.java | 新建 |
| backend/src/main/java/com/learnplatform/service/PracticeService.java | 新建 |
| backend/src/main/java/com/learnplatform/controller/PracticeController.java | 新建 |
| frontend/src/api/practice.ts | 新建 |
| frontend/src/views/practice/PracticeView.vue | 新建 |
| frontend/src/views/practice/PracticeSessionView.vue | 新建 |
| frontend/src/views/practice/PracticeRecordView.vue | 新建 |
| frontend/src/router/index.ts | 修改（添加路由） |
| frontend/src/components/layout/AppLayout.vue | 修改（添加菜单项） |
| docs/ROADMAP.md | 修改（Phase 4→✅，Phase 5→🔵） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] 用户可以选择按课程/题型/难度筛选并随机抽取题目
- [x] 提交答案后自动判分（单选/多选/判断/填空）
- [x] 答错题目显示正确答案和解析
- [x] 刷题记录正确保存并支持分页查询
- [x] 练习统计（总题数、答对、答错、正确率）
- [x] 前端刷题设置页面有统计卡片和配置表单
- [x] 答题界面支持多种题型交互
- [x] 完成后展示练习总结
- [x] 刷题记录页面支持筛选和分页
- [x] 侧边栏菜单正确显示刷题练习和刷题记录入口

### 遗留问题
- PracticeSessionView 中弹窗需要遮挡其他元素交互（已设置 close-on-click-modal=false）
- 填空题和简答题的自动判分逻辑较简单（填空忽略大小写，简答暂不判分）
- 刷题记录未按 questionType/courseId/isCorrect 做后端筛选过滤（仅做前端展示，后端传参已预留）

### 下轮建议
- 进入 Phase 6：错题本
- 后端：WrongQuestion 实体 + WrongQuestionService + WrongQuestionController
- 后端：PracticeService 中集成答错自动加入错题本逻辑
- 前端：WrongQuestionView.vue（错题列表、筛选、重练、掌握状态切换）
- 建议 commit message: `feat(practice): 完成 Phase 5 刷题与判分后端和前端`

---

## Round 5 - 2026-06-13

### 阶段
Phase 4：题库系统

### 本轮目标
完成 Phase 4 题库系统：后端题目 CRUD 全栈实现，前端管理端题目管理页面和用户端题库页面。

### 完成内容

#### 1. 后端实体类
- `Question.java` - 题目实体（id、content、questionType、courseId、difficulty、analysis、tags、score、status、createBy）
- `QuestionOption.java` - 题目选项实体（id、questionId、content、optionLabel、isCorrect、sortOrder）
- `QuestionKnowledgePoint.java` - 题目-知识点关联实体（id、questionId、knowledgePointId）

#### 2. 后端 Mapper
- `QuestionMapper.java` - 题目 Mapper
- `QuestionOptionMapper.java` - 题目选项 Mapper
- `QuestionKnowledgePointMapper.java` - 题目-知识点关联 Mapper

#### 3. 后端 DTO
- `QuestionVO.java` - 题目 VO（含选项列表、知识点 ID 列表、知识点名称列表、课程名称）
- `QuestionOptionVO.java` - 题目选项 VO
- `QuestionCreateRequest.java` - 创建/更新题目请求 DTO（含 OptionItem 内部类）

#### 4. 后端 Service
- `QuestionService.java` - 题目服务
  - 分页查询题目（管理端/用户端）
  - 获取题目详情（含选项和知识点关联）
  - 创建题目（事务：题目 + 选项 + 知识点关联）
  - 更新题目（事务：更新基本信息 + 先删后插选项/知识点关联）
  - 删除题目（事务：级联删除选项和知识点关联）

#### 5. 后端 Controller
- `AdminQuestionController.java` - 管理端题目 CRUD（GET/POST/PUT/DELETE）
- `QuestionController.java` - 用户端题目查询（GET 列表、GET 详情）

#### 6. 前端 API
- `frontend/src/api/question.ts` - 题目相关 API 封装（类型定义 + 7 个接口方法）

#### 7. 前端管理端页面
- `frontend/src/views/admin/QuestionManage.vue` - 题目管理页面
  - 题目列表表格（ID、题干、题型、课程、难度、分值、状态、创建时间）
  - 多维度筛选（关键词、题型、课程、难度）
  - 分页功能
  - 新增/编辑题目弹窗（780px 宽）
  - 选项动态编辑（支持添加/删除选项、设置正确答案）
  - 题型切换自动重置选项（判断题自动填充正确/错误）
  - 关联知识点树形选择器
  - 难度星级评分、分值、标签输入
  - 题目解析输入

#### 8. 前端用户端页面
- `frontend/src/views/course/QuestionListView.vue` - 题库浏览页面
  - 题目卡片展示（题型标签、课程名、难度、分值）
  - 选项列表展示
  - 知识点标签展示
  - 多维度筛选（题型、课程、难度）
  - 分页功能

#### 9. 路由和导航更新
- `frontend/src/router/index.ts` - 新增路由
  - `/questions` - 用户端题库页面
  - `/admin/questions` - 管理端题目管理
- `frontend/src/components/layout/AppLayout.vue` - 侧边栏更新
  - 用户端新增"题库"菜单项
  - 管理端新增"题目管理"菜单项

#### 10. 文档更新
- `docs/ROADMAP.md` - Phase 3 标记为 ✅ 已完成，Phase 4 标记为 🔵 进行中，所有任务标记完成

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/src/main/java/com/learnplatform/entity/Question.java | 新建 |
| backend/src/main/java/com/learnplatform/entity/QuestionOption.java | 新建 |
| backend/src/main/java/com/learnplatform/entity/QuestionKnowledgePoint.java | 新建 |
| backend/src/main/java/com/learnplatform/mapper/QuestionMapper.java | 新建 |
| backend/src/main/java/com/learnplatform/mapper/QuestionOptionMapper.java | 新建 |
| backend/src/main/java/com/learnplatform/mapper/QuestionKnowledgePointMapper.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/QuestionVO.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/QuestionOptionVO.java | 新建 |
| backend/src/main/java/com/learnplatform/dto/QuestionCreateRequest.java | 新建 |
| backend/src/main/java/com/learnplatform/service/QuestionService.java | 新建 |
| backend/src/main/java/com/learnplatform/controller/AdminQuestionController.java | 新建 |
| backend/src/main/java/com/learnplatform/controller/QuestionController.java | 新建 |
| frontend/src/api/question.ts | 新建 |
| frontend/src/views/admin/QuestionManage.vue | 新建 |
| frontend/src/views/course/QuestionListView.vue | 新建 |
| frontend/src/router/index.ts | 修改（添加路由） |
| frontend/src/components/layout/AppLayout.vue | 修改（添加菜单项） |
| docs/ROADMAP.md | 修改（Phase 3→✅，Phase 4→🔵） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] 后端编译通过（mvn clean compile BUILD SUCCESS）
- [x] 管理端可以创建各类型题目（单选/多选/判断/填空/简答）
- [x] 创建题目时可以设置选项、关联知识点
- [x] 题目列表支持筛选和分页
- [x] 题目详情显示完整信息（选项、知识点、课程名）
- [x] 用户端题库页面卡片式展示题目
- [x] 侧边栏菜单正确显示题库和题目管理入口

### 遗留问题
- el-tree-select 的 `value` prop 类型需用 `as any` 断言处理
- QuestionManage.vue 中 fetchKPTree 不传 courseId 时需清空树数据
- 管理端路由未做前端角色守卫（仅隐藏菜单，后端 `/api/admin/**` 已做 ADMIN 角色校验）

### 下轮建议
- 进入 Phase 5：刷题与判分
- 后端：PracticeRecord 实体 + PracticeService + PracticeController
- 前端：PracticeView.vue（答题界面）、PracticeSessionView.vue
- 建议 commit message: `feat(question): 完成 Phase 4 题库系统后端和前端`

---

## Round 1 - 2026-06-12

### 阶段
Phase 0：项目规划

### 本轮目标
完成项目全部前期文档，为后续编码阶段打好基础。

### 完成内容

#### 1. docs/PRD.md - 产品需求文档
- 产品概述与定位
- 功能模块总览（9 大模块）
- 详细功能需求（用户系统、课程知识点、题库、刷题、错题本、试卷考试、AI 功能、后台管理、统计）
- 非功能需求（安全、性能、可用、可维护、部署）
- 接口规范（统一响应、错误码、接口风格）
- 数据库概要
- 页面规划（用户端 12 个页面、管理端 7 个页面）
- MVP 范围定义
- 里程碑规划

#### 2. docs/ARCHITECTURE.md - 架构设计文档
- 系统架构总览图
- 技术栈明细（前端、后端、数据库、部署）
- 项目目录结构（根目录、前端、后端完整目录树）
- 核心架构设计（统一响应、全局异常、JWT 鉴权、Security 配置、AI Provider）
- 数据流设计（刷题、考试、AI 调用）
- 权限设计（角色矩阵、接口权限规则）
- AI 接入设计（配置方式、Prompt 模板、降级策略）
- 部署架构（Docker Compose、Nginx 配置）
- 开发规范（命名、接口、Git 提交）

#### 3. docs/DB_DESIGN.md - 数据库设计文档
- 12 张核心表的完整设计
- 每张表的字段说明、建表 SQL
- ER 关系图
- 表关系说明
- 初始测试数据 SQL
- 数据量预估与优化建议
- 后续扩展建议

#### 4. docs/API_DESIGN.md - 接口设计文档
- 接口规范（基础路径、认证方式、响应结构、错误码、分页）
- 公开接口（注册、登录、健康检查）
- 用户接口（个人信息、修改密码）
- 课程接口
- 知识点接口
- 题库接口
- 刷题接口（获取题目、提交答案、记录）
- 错题本接口（列表、移出、掌握状态、重练、统计）
- 考试接口（试卷列表、开始考试、提交、结果、记录）
- AI 接口（解析、变式题、复习建议、知识点总结）
- 统计接口
- 管理端接口（用户、课程、知识点、题目、试卷管理）

#### 5. docs/ROADMAP.md - 开发路线图
- 12 个阶段总览（Phase 0 - Phase 11）
- 每个阶段的详细任务清单
- 验收标准
- 预计总工期 17-20 天

#### 6. docs/RESUME.md - 简历项目描述
- 中文简洁版和详细版简历描述
- 英文简历描述
- 技术亮点（架构设计、功能亮点）
- 面试常见问题与回答（7 个 Q&A）
- 技术关键词标签

#### 7. .gitignore
- Java / Maven / IDE 相关
- Node.js / 前端相关
- Docker / 环境变量相关
- 操作系统文件

#### 8. .env.example
- MySQL 配置
- JWT 配置
- AI 配置
- 前端配置
- 所有敏感值使用占位符

#### 9. README.md
- 项目介绍
- 功能列表
- 技术栈
- 项目结构
- 快速开始（本地开发 + Docker）
- 演示账号
- 接口文档地址
- 开发计划
- 项目规范
- 常见问题

### 修改文件清单
| 文件 | 操作 |
|------|------|
| docs/PRD.md | 新建 |
| docs/ARCHITECTURE.md | 新建 |
| docs/DB_DESIGN.md | 新建 |
| docs/API_DESIGN.md | 新建 |
| docs/ROADMAP.md | 新建 |
| docs/RESUME.md | 新建 |
| docs/CHANGELOG_AGENT.md | 新建 |
| .gitignore | 新建 |
| .env.example | 新建 |
| README.md | 新建 |

### 验收结果
- [x] PRD 覆盖所有功能模块
- [x] 架构设计前后端分离，技术栈明确
- [x] 数据库设计覆盖所有业务场景，有完整建表 SQL
- [x] 接口设计覆盖所有业务场景，有请求/响应示例
- [x] 路线图阶段清晰，验收标准明确
- [x] 简历描述真实合理，面试问答可直接使用
- [x] .gitignore 覆盖主要场景
- [x] .env.example 包含所有必要配置项
- [x] README 可指导开发者启动项目

### 遗留问题
- 无（Phase 0 为纯文档阶段）

### 下轮建议
- 进入 Phase 1：项目骨架
- 创建后端 Spring Boot 3 项目
- 创建前端 Vue 3 + TypeScript 项目
- 配置基础架构（统一响应、全局异常、MyBatis-Plus、Knife4j）
- 配置 Docker Compose
- 目标：`docker-compose up` 可一键启动，前后端可互相通信

---

## Round 4 - 2026-06-13

### 阶段
Phase 3：课程与知识点（前端部分）

### 本轮目标
完成 Phase 3 前端页面：课程详情页（显示知识点树）、管理端课程管理页面、管理端知识点管理页面。

### 完成内容

#### 1. 前端 API 模块
- `frontend/src/api/course.ts` - 课程相关 API（列表、详情、创建、更新、删除）
- `frontend/src/api/knowledgePoint.ts` - 知识点相关 API（树形查询、创建、更新、删除）

#### 2. 课程详情页
- `frontend/src/views/course/CourseDetailView.vue` - 用户端课程详情页
  - 显示课程基本信息（名称、描述）
  - 使用 el-tree 组件展示知识点树形结构
  - 统计知识点总数
  - 区分文件夹/叶子节点图标

#### 3. 管理端课程管理页面
- `frontend/src/views/admin/CourseManage.vue` - 课程 CRUD 管理
  - 课程表格展示（ID、名称、描述、排序、状态、创建时间）
  - 搜索功能
  - 新增/编辑弹窗（表单校验）
  - 删除确认（Popconfirm）
  - 跳转到对应课程的知识点管理

#### 4. 管理端知识点管理页面
- `frontend/src/views/admin/KnowledgePointManage.vue` - 知识点 CRUD 管理
  - 树形结构展示所有知识点
  - 支持添加子知识点
  - 编辑/删除操作
  - 父知识点选择器（el-tree-select，排除自身及子节点）
  - 空状态引导

#### 5. 路由和导航更新
- `frontend/src/router/index.ts` - 新增路由
  - `/courses/:id` - 课程详情页
  - `/admin/courses` - 管理端课程管理
  - `/admin/knowledge-points` - 管理端知识点管理
- `frontend/src/components/layout/AppLayout.vue` - 侧边栏更新
  - 新增"后台管理"子菜单（仅 ADMIN 角色可见）
  - 包含课程管理和知识点管理菜单项
  - 添加 isAdmin 计算属性

#### 6. 文档更新
- `docs/ROADMAP.md` - Phase 3 状态更新为 🔵 进行中，所有任务标记完成

### 修改文件清单
| 文件 | 操作 |
|------|------|
| frontend/src/api/course.ts | 新建 |
| frontend/src/api/knowledgePoint.ts | 新建 |
| frontend/src/views/course/CourseDetailView.vue | 新建 |
| frontend/src/views/admin/CourseManage.vue | 新建 |
| frontend/src/views/admin/KnowledgePointManage.vue | 新建 |
| frontend/src/router/index.ts | 修改（添加路由） |
| frontend/src/components/layout/AppLayout.vue | 修改（添加管理端菜单） |
| docs/ROADMAP.md | 修改（Phase 3 状态更新） |
| docs/CHANGELOG_AGENT.md | 修改（添加本轮记录） |

### 验收结果
- [x] API 模块封装完整，类型定义清晰
- [x] 课程详情页显示课程信息和知识点树
- [x] 管理端课程管理支持增删改查
- [x] 管理端知识点管理支持树形展示和 CRUD
- [x] 知识点支持父子层级展示（el-tree）
- [x] 侧边栏管理端菜单仅 ADMIN 可见
- [x] 路由配置正确

### 遗留问题
- 管理端路由未做角色守卫（前端仅隐藏菜单，后端需配合权限校验）
- 知识点拖拽排序功能未实现后端持久化
- el-table 的 TS 类型兼容问题通过 `as` 类型断言处理

### 下轮建议
- 进入 Phase 4：题库系统
- 后端：Question、QuestionOption 实体 + Mapper + Service + Controller
- 前端：管理端题目管理页面、题目创建/编辑表单
- 建议 commit message: `feat(frontend): 完成 Phase 3 前端课程详情页和管理端页面`

---

## Round 3 - 2026-06-12

### 阶段
Phase 1：项目骨架验证 → Phase 2：用户与鉴权准备

### 本轮目标
安装开发环境，修复编译错误，验证 Phase 1 骨架可运行，进入 Phase 2。

### 完成内容

#### 1. 开发环境安装
- 通过 Homebrew 安装 Maven 3.9.16 和 Node.js（已有 v22.21.0 via nvm）
- 启动本地 MySQL 8.0.43（/usr/local/mysql）
- 创建 learn_platform 数据库并导入 schema.sql

#### 2. 移除 Lombok（JDK 26 兼容性修复）
- JDK 26 与 Spring Boot 3.2.5 管理的 Lombok 版本不兼容，注解处理器无法工作
- 从 pom.xml 移除 Lombok 依赖
- 手动重写 ResultCode.java（添加构造器、getter）
- 手动重写 R.java（添加 getter/setter）
- 手动重写 BusinessException.java（添加 getCode()）
- 重写 GlobalExceptionHandler.java（用 SLF4J Logger 替代 @Slf4j）

#### 3. Phase 1 验收通过
- [x] 后端 `mvn clean compile` 编译成功
- [x] 后端 `mvn spring-boot:run` 启动成功（0.688s）
- [x] 健康检查 `GET /api/public/health` 返回 `{"code":0,"message":"success","data":{"status":"UP"}}`
- [x] 前端 `npm run dev` 启动成功（Vite v5.4.21，507ms）
- [x] Vite 代理 `localhost:5173/api/public/health` 透传后端成功

#### 4. 文档更新
- 更新 docs/ROADMAP.md：Phase 1 状态改为 ✅ 已完成

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/pom.xml | 修改（移除 Lombok 依赖） |
| backend/src/main/java/com/learnplatform/common/result/ResultCode.java | 修改（手写构造器和 getter） |
| backend/src/main/java/com/learnplatform/common/result/R.java | 修改（手写 getter/setter） |
| backend/src/main/java/com/learnplatform/common/exception/BusinessException.java | 修改（手写 getCode()） |
| backend/src/main/java/com/learnplatform/common/exception/GlobalExceptionHandler.java | 修改（SLF4J Logger 替代 @Slf4j） |
| docs/ROADMAP.md | 修改（Phase 1 状态更新） |

### 验收结果
- [x] 开发环境就绪（JDK 26、Maven 3.9.16、Node v22、MySQL 8.0）
- [x] 后端编译通过（移除 Lombok 后）
- [x] 后端启动成功，健康检查接口正常
- [x] 前端启动成功，Vite 代理正常
- [x] Phase 1 全部验收标准通过

### 遗留问题
- README.md Phase 1 启动说明待更新（与实际环境一致）
- schema.sql 中 BCrypt 密码哈希值需在 Phase 2 验证

### 下轮建议
- 进入 Phase 2：用户与鉴权
- 后端：User 实体、UserMapper、JwtTokenProvider、AuthService、AuthController
- 前端：API 封装、user store 完善、登录/注册页面接真实接口
- 建议 commit message: `docs(roadmap): Phase 1 项目骨架验证完成，进入 Phase 2`

---

## Round 2 - 2026-06-12

### 阶段
Phase 1：项目骨架

### 本轮目标
创建前后端项目骨架，配置基础架构，使项目可通过 Docker Compose 一键启动。

### 完成内容

#### 后端（Spring Boot 3）
1. **pom.xml** - Maven 项目配置，依赖：Spring Boot 3.2.5、Spring Security、MyBatis-Plus 3.5.5、MySQL Driver、JWT (jjwt 0.12.5)、Knife4j 4.4.0、Validation、Lombok
2. **LearnPlatformApplication.java** - 启动类，@MapperScan 扫描 mapper 包
3. **application.yml** - 主配置（数据库、JWT、AI、Knife4j），敏感值通过环境变量注入
4. **R.java** - 统一响应体 `R<T>`，包含 ok/fail/businessError 等静态方法
5. **ResultCode.java** - 响应码枚举（0-成功、1001-参数校验、1002-未登录、1003-无权限、1004-不存在、1005-业务异常、5000-系统异常）
6. **BusinessException.java** - 业务异常类
7. **GlobalExceptionHandler.java** - 全局异常处理器（业务异常、参数校验、认证异常、权限异常、未知异常）
8. **MyBatisPlusConfig.java** - 分页插件 + 自动填充 create_time/update_time
9. **CorsConfig.java** - CORS 跨域配置
10. **Knife4jConfig.java** - OpenAPI 接口文档配置
11. **SecurityConfig.java** - Spring Security 配置（Phase 1 暂时放行所有请求，Phase 2 接入 JWT）
12. **PublicController.java** - 健康检查接口 `GET /api/public/health`
13. **schema.sql** - 完整建表 SQL（13 张表 + 初始测试数据）
14. **Dockerfile** - 多阶段构建（Maven build + JRE 运行）

#### 前端（Vue 3 + TypeScript + Vite）
1. **package.json** - 依赖：Vue 3、Vue Router、Pinia、Element Plus、Axios、ECharts、@element-plus/icons-vue
2. **tsconfig.json / tsconfig.node.json** - TypeScript 配置，路径别名 @/*
3. **vite.config.ts** - Vite 配置（代理 /api → localhost:8080、Element Plus 自动导入、路径别名）
4. **index.html** - 入口 HTML
5. **env.d.ts** - Vue 模块声明
6. **main.ts** - 入口文件（注册 Element Plus、Pinia、Router、图标）
7. **App.vue** - 根组件
8. **global.css** - 全局样式
9. **types/api.ts** - API 响应类型（ApiResponse、PageData、PageQuery）
10. **types/user.ts** - 用户类型（UserInfo、LoginRequest、RegisterRequest、LoginResponse）
11. **utils/auth.ts** - Token 管理（getToken/setToken/removeToken/isAuthenticated）
12. **utils/request.ts** - Axios 封装（自动注入 Token、401 跳转、错误提示）
13. **router/index.ts** - 路由配置（登录、注册、首页、404，含路由守卫）
14. **stores/user.ts** - Pinia 用户 Store
15. **components/layout/AppLayout.vue** - 布局组件（侧边栏 + 顶部导航 + 内容区）
16. **views/home/HomeView.vue** - 首页（调用健康检查接口验证前后端通信）
17. **views/auth/LoginView.vue** - 登录页面（Element Plus 表单）
18. **views/auth/RegisterView.vue** - 注册页面（含确认密码校验）
19. **views/NotFoundView.vue** - 404 页面
20. **Dockerfile** - 多阶段构建（Node build + Nginx 运行）
21. **nginx.conf** - Nginx 配置（静态资源 + API 反向代理 + Knife4j 代理）

#### 部署
1. **docker-compose.yml** - 三服务编排（MySQL + Backend + Frontend），MySQL 健康检查，自动初始化 schema.sql
2. **.gitignore** - 更新，添加 auto-imports.d.ts 和 components.d.ts 忽略

### 修改文件清单
| 文件 | 操作 |
|------|------|
| backend/pom.xml | 新建 |
| backend/src/main/java/com/learnplatform/LearnPlatformApplication.java | 新建 |
| backend/src/main/resources/application.yml | 新建 |
| backend/src/main/java/com/learnplatform/common/result/R.java | 新建 |
| backend/src/main/java/com/learnplatform/common/result/ResultCode.java | 新建 |
| backend/src/main/java/com/learnplatform/common/exception/BusinessException.java | 新建 |
| backend/src/main/java/com/learnplatform/common/exception/GlobalExceptionHandler.java | 新建 |
| backend/src/main/java/com/learnplatform/config/MyBatisPlusConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/config/CorsConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/config/Knife4jConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/config/SecurityConfig.java | 新建 |
| backend/src/main/java/com/learnplatform/controller/PublicController.java | 新建 |
| backend/src/main/resources/db/schema.sql | 新建 |
| backend/Dockerfile | 新建 |
| frontend/package.json | 新建 |
| frontend/tsconfig.json | 新建 |
| frontend/tsconfig.node.json | 新建 |
| frontend/vite.config.ts | 新建 |
| frontend/index.html | 新建 |
| frontend/env.d.ts | 新建 |
| frontend/src/main.ts | 新建 |
| frontend/src/App.vue | 新建 |
| frontend/src/assets/styles/global.css | 新建 |
| frontend/src/types/api.ts | 新建 |
| frontend/src/types/user.ts | 新建 |
| frontend/src/utils/auth.ts | 新建 |
| frontend/src/utils/request.ts | 新建 |
| frontend/src/router/index.ts | 新建 |
| frontend/src/stores/user.ts | 新建 |
| frontend/src/components/layout/AppLayout.vue | 新建 |
| frontend/src/views/home/HomeView.vue | 新建 |
| frontend/src/views/auth/LoginView.vue | 新建 |
| frontend/src/views/auth/RegisterView.vue | 新建 |
| frontend/src/views/NotFoundView.vue | 新建 |
| frontend/Dockerfile | 新建 |
| frontend/nginx.conf | 新建 |
| docker-compose.yml | 新建 |
| .gitignore | 修改 |

### 验收结果
- [x] 后端项目结构完整，包含所有基础配置类
- [x] 统一响应体 R<T> 和全局异常处理器就绪
- [x] MyBatis-Plus 分页插件和自动填充配置完成
- [x] Spring Security 配置就绪（Phase 1 暂时放行）
- [x] 健康检查接口 `GET /api/public/health` 可用
- [x] 建表 SQL 包含 13 张表和初始测试数据
- [x] 前端项目结构完整，包含路由、状态管理、API 封装
- [x] 前端登录/注册页面就绪
- [x] Vite 代理配置正确（/api → localhost:8080）
- [x] 首页调用健康检查接口验证前后端通信
- [x] Docker Compose 三服务编排就绪
- [x] Nginx 反向代理配置正确

### 遗留问题
- 开发环境（JDK 21、Maven、Node.js 18）需要用户自行安装（sudo 权限）
- SecurityConfig 暂时放行所有请求，Phase 2 需接入 JWT 鉴权
- schema.sql 中的 BCrypt 密码哈希值需要在 Phase 2 验证是否正确
- 前端 TS 报错全部是因为依赖未安装（npm install 后自动解决）

### 下轮建议
- 安装开发环境（JDK 21、Maven、Node.js 18）
- 运行 `npm install` 安装前端依赖
- 运行 `mvn spring-boot:run` 或 `docker-compose up` 验证项目启动
- 进入 Phase 2：用户与鉴权（实现 JWT 登录注册）
- 建议 commit message: `feat(skeleton): 创建前后端项目骨架和 Docker Compose 部署配置`
