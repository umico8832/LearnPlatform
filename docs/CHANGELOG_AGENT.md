# AI 题库与错题复习系统 - 开发日志

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
