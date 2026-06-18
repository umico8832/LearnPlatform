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

## Round 80 - 2026-06-18

### 阶段
Phase 15：AI 学习画像与个性化推荐 — 单题错因分析

### 本轮目标
实现单题错因分析功能，针对具体题目分析用户多次作答的错误模式变化，包含作答历史、掌握趋势和错误模式描述。

### 完成内容
1. **后端 VO 扩展**（`LearningDiagnosisVO`）新增 2 个内部类：
   - `QuestionErrorAnalysis`：单题错因分析结果（题目信息、作答统计、正确率、掌握程度、掌握趋势、作答历史、错误模式描述）
   - `AttemptHistory`：单次作答记录（recordId、userAnswer、isCorrect、answerTime、createTime）
2. **后端 Service 新增**（`LearningDiagnosisService`）：
   - `analyzeQuestionError(userId, questionId)`：获取用户对该题所有练习记录，计算正确率、掌握趋势（IMPROVING/STAGNANT/DECLINING），生成错误模式描述
   - `buildErrorPattern()`：分析连续错误、反复出错、掌握程度等模式
   - 掌握趋势算法：最近 5 次正确率 vs 之前正确率，差异 ≥20% 判定为提升/下降
3. **后端 Controller 新增**（`StatisticsController`）：
   - `GET /api/statistics/question-error-analysis?questionId=` — 单题错因分析接口
4. **前端 API 新增**（`statistics.ts`）：
   - `AttemptHistory`、`QuestionErrorAnalysis` TypeScript 接口
   - `getQuestionErrorAnalysis(questionId)` API 函数
5. **前端页面增强**（`LearningDiagnosisView.vue`）：
   - 反复错题详情表格新增"错因分析"操作按钮
   - 单题错因分析弹窗：题目信息卡片、4 个核心统计指标（el-statistic）、掌握趋势 el-alert、掌握程度标签、错误模式分析框（橙色左边框）、el-timeline 作答历史时间线
   - 辅助 CSS：`.error-analysis-header`、`.error-analysis-question`、`.error-analysis-tags`、`.error-pattern-box`

### 修改文件清单
- `backend/src/main/java/com/learnplatform/dto/LearningDiagnosisVO.java`（新增 QuestionErrorAnalysis + AttemptHistory 内部类）
- `backend/src/main/java/com/learnplatform/service/LearningDiagnosisService.java`（新增 analyzeQuestionError + buildErrorPattern 方法）
- `backend/src/main/java/com/learnplatform/controller/StatisticsController.java`（新增 question-error-analysis 接口）
- `frontend/src/api/statistics.ts`（新增 TS 接口 + API 函数）
- `frontend/src/views/statistics/LearningDiagnosisView.vue`（新增错因分析弹窗 + 按钮 + CSS）

### 验收结果
- [x] `cd backend && mvn test`：211 个测试全部通过
- [x] `cd frontend && npm run build`：构建成功

### 遗留问题
- 无

### 下轮建议
- 继续 Phase 14 候选方向：代码执行动画、SQL 执行顺序可视化
- 或进入 Phase 16：题目投稿与 AI 题库生产
- 或补充单题错因分析的后端单元测试

---

## Round 79 - 2026-06-18

### 阶段
Phase 15：AI 学习画像与个性化推荐 — 错题归因分析增强

### 本轮目标
增强学习诊断页面的错因分析能力，新增 5 个维度的错题归因数据，帮助用户更深入理解自己的错误模式。

### 完成内容
1. **后端 VO 扩展**（`LearningDiagnosisVO.ErrorPatternSummary`）新增 5 个字段：
   - `questionTypeDistribution`：错题题型分布（单选/多选/判断/填空/简答各自错题数）
   - `difficultyDistribution`：错题难度分布（按 1-5 星统计）
   - `knowledgePointErrors`：知识点错因排名（Top 8，含错题数、练习数、正确率）
   - `repeatedErrors`：反复错题详情（wrongCount >= 2，最多 10 条，含题型、难度、掌握度、知识点、课程）
   - `weeklyErrorTrend`：近 4 周错题趋势（每周错题数，按周一起止）
2. **新增内部类**：`KnowledgePointErrorRank`、`RepeatedErrorItem`
3. **后端 Service 增强**：`computeErrorPatterns` 方法签名扩展（新增 allPoints、questionToKps 参数），新增 `buildWeeklyErrorTrend` 方法
4. **AI Prompt 增强**：`buildAiAdviceUserPrompt` 现在包含错题题型分布、难度分布、知识点错因排名和每周错题趋势，让 AI 学习建议更精准
5. **前端页面增强**（`LearningDiagnosisView.vue`）：
   - 错题题型分布（进度条可视化）
   - 错题难度分布（星级着色进度条）
   - 近 4 周错题趋势（柱状图）
   - 知识点错因排名表（知识点、错题数、练习数、正确率）
   - 反复错题详情表（题目、题型、难度、错次、掌握度、知识点、找相似题操作）
6. **前端 TypeScript 类型更新**（`statistics.ts`）：新增 `KnowledgePointErrorRank`、`RepeatedErrorItem`、`WeeklyErrorTrendItem` 接口，扩展 `ErrorPatternSummary`
7. **前端辅助函数**：`getDifficultyColor`、`getWeeklyBarHeight`、`getMasteryLevelType`、`getMasteryLevelLabel`
8. **CSS 补充**：`.chart-bar.error-trend` 样式

### 修改文件清单
- `backend/src/main/java/com/learnplatform/dto/LearningDiagnosisVO.java`（扩展 ErrorPatternSummary + 新增内部类）
- `backend/src/main/java/com/learnplatform/service/LearningDiagnosisService.java`（computeErrorPatterns 增强 + buildWeeklyErrorTrend + AI Prompt 增强）
- `frontend/src/api/statistics.ts`（新增 TS 接口）
- `frontend/src/views/statistics/LearningDiagnosisView.vue`（新增 5 个可视化区域 + 辅助函数 + CSS）

### 验收结果
- [x] `cd backend && mvn test`：211 个测试全部通过
- [x] `cd frontend && npm run build`：构建成功

### 遗留问题
- 无

### 下轮建议
- 继续 Phase 15 深化：单题错因分析（针对具体题目分析用户多次作答的错误模式变化）
- 或继续 Phase 14 候选方向：代码执行动画、SQL 执行顺序可视化
- 或进入 Phase 16：题目投稿与 AI 题库生产

---

## Round 78 - 2026-06-17

### 阶段
Phase 15：AI 学习画像与个性化推荐 — LearningDiagnosisService 单元测试

### 本轮目标
补写 LearningDiagnosisService 单元测试，覆盖学习诊断、相似题推荐、AI 建议三大核心能力，提升后端测试覆盖率。

### 完成内容
1. **新建 `LearningDiagnosisServiceTest`**：30 个单元测试，覆盖：
   - **getDiagnosis 基本场景**（4 个）：空数据返回零值、正确率计算（50%/100%）、连续天数
   - **学习习惯**（3 个）：空记录默认值、有记录时题型/课程偏好、7 天趋势结构
   - **错因分析**（4 个）：无错题、反复出错（wrongCount>=3）、近 7 天新增错题、高频错题课程排序
   - **知识点薄弱诊断**（3 个）：60% 正确率 => NEEDS_REVIEW、90% 正确率被过滤、未开始练习 => NOT_STARTED
   - **每日推荐**（3 个）：从反复错题推荐（ERROR_PRONE）、限制最多 5 题、已掌握错题跳过
   - **每日建议**（2 个）：有学习记录时建议、无记录时建议
   - **课程掌握概况**（1 个）：正确率、练习次数
   - **findSimilarQuestions**（4 个）：题目不存在返回空、同知识点相似题、排除源题、已练习标记
   - **findSimilarQuestions 排序**（1 个）：相似度降序排序
   - **generateAiAdvice**（3 个）：成功调用并记录日志、失败记录日志、Prompt 包含诊断数据
   - **generateAiAdviceStream**（2 个）：流式输出拼接、失败记录日志

### 修改文件清单
- `backend/src/test/java/com/learnplatform/service/LearningDiagnosisServiceTest.java`（新增，30 个测试）

### 验收结果
- [x] `cd backend && mvn test`：211 个测试全部通过（新增 30 个，原有 181 个）
- [x] LearningDiagnosisService 核心逻辑全覆盖：诊断、推荐、AI 建议、相似题

### 遗留问题
- 无

### 下轮建议
- 可继续 Phase 15 深化：错题归因分析增强
- 或继续 Phase 14 候选方向：代码执行动画、SQL 执行顺序可视化
- 或进入 Phase 16：题目投稿与 AI 题库生产
- 建议 commit message: `test(backend): 补写 LearningDiagnosisService 单元测试（30 个）`

---

## Round 77 - 2026-06-17

### 阶段
Phase 15：AI 学习画像与个性化推荐 — 相似题推荐

### 本轮目标
在 Phase 15 学习诊断和 AI 个性化建议基础上，实现相似题推荐功能。当用户答错题目或在学习诊断页面浏览推荐题目时，可以一键查找相似题目进行巩固练习，形成"发现薄弱 → 找相似题 → 针对性练习"的学习闭环。

### 完成内容
1. **后端 `SimilarQuestionVO`（新建）**：相似题推荐 DTO，包含源题目信息和相似题目列表（`SimilarItem` 内嵌类），每项含相似度得分、相似原因、是否已练过等字段。
2. **后端 `LearningDiagnosisService` 新增 `findSimilarQuestions` 方法**：
   - 基于多维相似度评分（同知识点 +40、同题型 +30、同难度 +20、同课程 +10）对所有候选题目打分。
   - 过滤已删除和相似度不足的题目，按得分降序返回 Top N。
   - 标注用户是否已练习过每道相似题，方便用户选择未练过的题。
3. **后端 `StatisticsController` 新增 `/similar-questions` 接口**：`GET /api/statistics/similar-questions?questionId=&limit=` 接口。
4. **前端 `statistics.ts` 扩展**：新增 `SimilarQuestionItem`、`SimilarQuestions` TypeScript 接口 + `getSimilarQuestions()` API 函数。
5. **前端 `LearningDiagnosisView.vue` 增强**：
   - 推荐题目表格新增"找相似题"操作列。
   - 新增相似题推荐弹窗（相似度进度条、相似原因、已练过标签、开始练习按钮）。
   - `getSimilarityColor`、`loadSimilarQuestions`、`startSimilarPractice` 辅助函数。
6. **前端 `WrongQuestionView.vue` 增强**：
   - 每张错题卡片底部新增"🔍 找相似题"按钮。
   - 新增相似题推荐弹窗（与诊断页共用同一交互模式）。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/dto/SimilarQuestionVO.java`（新增）
- `backend/src/main/java/com/learnplatform/service/LearningDiagnosisService.java`（修改：新增 findSimilarQuestions + questionToKps 辅助方法）
- `backend/src/main/java/com/learnplatform/controller/StatisticsController.java`（修改：新增 SimilarQuestionVO import + /similar-questions 接口）
- `frontend/src/api/statistics.ts`（修改：新增 SimilarQuestionItem、SimilarQuestions 接口 + getSimilarQuestions 函数）
- `frontend/src/views/statistics/LearningDiagnosisView.vue`（修改：新增找相似题按钮、弹窗、辅助函数和 CSS）
- `frontend/src/views/practice/WrongQuestionView.vue`（修改：新增找相似题按钮、弹窗、辅助函数和 CSS）

### 验收结果
- [x] `cd backend && mvn compile`：编译成功
- [x] `cd backend && mvn test`：181 个测试全部通过
- [x] `cd frontend && npx vue-tsc --noEmit`：TypeScript 检查通过，无错误
- [x] `cd frontend && npm test`：21 个测试文件、187 个测试全部通过
- [x] 后端接口 `GET /api/statistics/similar-questions` 设计完成
- [x] 前端错题本和学习诊断页面均集成相似题推荐入口

### 遗留问题
- 相似题推荐基于结构化属性（知识点、题型、难度、课程）打分，不涉及文本语义分析，后续可引入向量相似度增强
- 错题本弹窗尺寸在移动端可能需要适配

### 下轮建议
- 可继续 Phase 15 深化：补写 LearningDiagnosisService 单元测试
- 或继续 Phase 14 候选方向：代码执行动画
- 建议 commit message: `feat(ai): Phase 15 相似题推荐（错题巩固闭环）`

---

## Round 76 - 2026-06-17

### 阶段
Phase 15：AI 学习画像与个性化推荐 — AI 个性化学习建议

### 本轮目标
在 Round 75 完成的学习诊断基础上，将规则生成的每日建议升级为 AI 生成的个性化学习建议。基于完整诊断数据（薄弱知识点、错因分析、学习习惯、推荐题目等）构建上下文丰富的 Prompt，通过 SSE 流式输出 Markdown 格式建议。

### 完成内容
1. **后端 `LearningDiagnosisService` 新增 AI 建议能力**：
   - 新增 `generateAiAdvice(userId)` 同步方法：基于诊断数据构建 Prompt，调用 AiProvider.chat 生成建议，带日志记录。
   - 新增 `generateAiAdviceStream(userId, onContent)` 流式方法：同样基于诊断数据，通过 SSE 流式输出。
   - 新增 `buildAiAdviceSystemPrompt()` 方法：定义 AI 学习顾问角色和 8 条输出要求（具体可操作、短期/中期计划、Markdown 格式、500-800 字等）。
   - 新增 `buildAiAdviceUserPrompt(diagnosis)` 方法：将完整诊断数据（基本数据、薄弱知识点、课程掌握概况、错因分析、学习习惯、推荐题目）格式化为结构化 Prompt。
   - 依赖注入 `AiProvider` 和 `AiService`（日志记录）。
2. **后端 `StatisticsController` 新增 2 个接口**：
   - `POST /api/statistics/ai-advice`：同步获取 AI 个性化学习建议。
   - `POST /api/statistics/ai-advice/stream`：SSE 流式返回 AI 个性化学习建议。
   - 注入 `aiTaskExecutor`，复用现有 SSE stream 基础设施。
3. **前端 `statistics.ts` 扩展**：
   - 新增 `getAiAdvice()` 同步 API 函数。
   - 新增 `getAiAdviceStream()` 流式 API 函数（使用 fetch + Bearer Token）。
4. **前端 `LearningDiagnosisView.vue` 增强**：
   - 每日建议卡片新增 "🤖 AI 个性化建议" 按钮（loading 状态、禁用控制）。
   - 新增 AI 个性化建议卡片区域（MarkdownRenderer 渲染、流式状态标签、loading 占位）。
   - 新增 `generateAiAdvice()` 函数：fetch SSE 流式读取、逐段解析 `data:` 事件、拼接内容。
   - 新增 AI 建议相关 CSS 样式（左侧蓝色边框、Markdown 深度样式适配）。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/service/LearningDiagnosisService.java`（修改：新增 AiProvider/AiService 依赖 + generateAiAdvice/generateAiAdviceStream 方法 + Prompt 构建）
- `backend/src/main/java/com/learnplatform/controller/StatisticsController.java`（修改：新增 aiTaskExecutor 依赖 + /ai-advice 和 /ai-advice/stream 接口 + sendSse 方法）
- `frontend/src/api/statistics.ts`（修改：新增 getAiAdvice + getAiAdviceStream 函数）
- `frontend/src/views/statistics/LearningDiagnosisView.vue`（修改：新增 AI 建议按钮、卡片、流式渲染逻辑和样式）

### 验收结果
- [x] `cd backend && mvn compile`：编译成功
- [x] `cd backend && mvn test`：181 个测试全部通过
- [x] `cd frontend && npx vue-tsc --noEmit`：TypeScript 检查通过，无错误
- [x] `cd frontend && npm test`：21 个测试文件、187 个测试全部通过
- [x] 后端接口 `POST /api/statistics/ai-advice` 和 `POST /api/statistics/ai-advice/stream` 设计完成
- [x] 前端 AI 建议按钮、流式渲染、Markdown 渲染集成完成

### 遗留问题
- AI 调用依赖 AI_ENABLED 配置，未配置时需要前端友好提示（当前由 AiProvider 内部处理）
- AI 建议生成会消耗一次 AI 调用配额
- 学习诊断缓存和 AI 建议是分开的（AI 建议不缓存，每次实时生成）

### 下轮建议
- 可继续 Phase 15 深化：错题归因分析增强、相似题推荐
- 或补写 LearningDiagnosisService 单元测试
- 建议 commit message: `feat(ai): Phase 15 AI 个性化学习建议（SSE 流式输出）`

---

## Round 75 - 2026-06-17

### 阶段
Phase 15：AI 学习画像与个性化推荐 — P0 学习诊断与每日推荐

### 本轮目标
进入 Phase 15，实现学习诊断核心能力：知识点薄弱诊断、错因分析、学习习惯分析和每日推荐题目，为后续 AI 个性化推荐打下数据基础。

### 完成内容
1. **后端 `LearningDiagnosisVO`**：新建学习诊断 VO，包含 8 个嵌套类（WeakPoint、CourseMastery、ErrorPatternSummary、CourseErrorCount、LearningHabit、RecommendedQuestion），覆盖知识点薄弱诊断、课程掌握概况、错因分析汇总、学习习惯分析、每日推荐题目和每日建议文本。
2. **后端 `LearningDiagnosisService`**：新建学习诊断服务（约 480 行），基于用户练习记录、错题本、知识点关联等数据，综合分析：
   - 知识点薄弱诊断（Top 8 薄弱知识点，正确率 < 70%，优先级排序）
   - 课程掌握概况（按正确率排序，含薄弱知识点数统计）
   - 错因分析汇总（掌握程度分布、反复出错题目数、近 7 天新增错题、高频错题课程 Top 5）
   - 学习习惯分析（日均刷题、偏好题型/课程、学习频次评级、近 7 天趋势）
   - 每日推荐题目（5 道，优先高频错题间隔复习 → 薄弱知识点强化 → 未练习题目）
   - 每日学习建议文本（基于连续天数、薄弱知识点、反复错题、学习频次规则生成）
3. **后端 `StatisticsController` 新增 `/learning-diagnosis` 接口**：GET 接口，返回完整学习诊断数据。
4. **后端 `RedisConfig` 新增 `learningDiagnosis` 缓存区域**：TTL 10 分钟。
5. **前端 `statistics.ts` 类型扩展**：新增 WeakPoint、CourseMastery、CourseErrorCount、ErrorPatternSummary、LearningHabit、RecommendedQuestion、LearningDiagnosis 共 7 个 TypeScript 接口 + `getLearningDiagnosis()` API 函数。
6. **前端 `LearningDiagnosisView.vue`**：新建学习诊断页面，包含每日建议卡片、4 个核心指标卡片、知识点薄弱诊断表格、学习习惯分析（含纯 CSS 近 7 天趋势柱状图）、错因分析（掌握程度分布进度条 + 高频错题课程）、课程掌握概况表格、每日推荐题目表格（含"开始练习"按钮跳转 PracticeSession）。
7. **前端路由 `learning-diagnosis`** 和侧边栏 `🧠 学习诊断` 入口（TrendCharts 图标）。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/dto/LearningDiagnosisVO.java`（新增）
- `backend/src/main/java/com/learnplatform/service/LearningDiagnosisService.java`（新增）
- `backend/src/main/java/com/learnplatform/controller/StatisticsController.java`（修改：新增 LearningDiagnosisService 注入 + /learning-diagnosis 接口）
- `backend/src/main/java/com/learnplatform/config/RedisConfig.java`（修改：新增 learningDiagnosis 缓存区域）
- `frontend/src/api/statistics.ts`（修改：新增 7 个接口 + getLearningDiagnosis 函数）
- `frontend/src/views/statistics/LearningDiagnosisView.vue`（新增）
- `frontend/src/router/index.ts`（修改：新增 learning-diagnosis 路由）
- `frontend/src/components/layout/AppLayout.vue`（修改：新增学习诊断侧边栏菜单项 + TrendCharts 图标引入）
- `docs/CHANGELOG_AGENT.md`（修改：新增 Round 75 记录）
- `docs/ROADMAP.md`（修改：Phase 15 状态更新）
- `docs/HANDOFF.md`（修改：当前阶段和下一步建议更新）

### 验收结果
- [x] `cd backend && mvn compile`：编译成功
- [x] `cd backend && mvn test`：30 个测试全部通过
- [x] `cd frontend && npx vue-tsc --noEmit`：TypeScript 检查通过，无错误
- [x] `cd frontend && npm test`：21 个测试文件、187 个测试全部通过
- [x] 后端接口 `GET /api/statistics/learning-diagnosis` 设计完成
- [x] 前端页面包含 6 个可视化区域：每日建议、核心指标、薄弱诊断、学习习惯、错因分析、课程掌握、推荐题目

### 遗留问题
- 每日建议目前为规则生成，后续可接入 AI 生成更精准的建议文本
- 推荐题目的"开始练习"跳转到 PracticeSession 需要 PracticeSessionView 支持 questionIds 参数（目前支持 courseId 等参数，可能需要适配）
- LearningDiagnosisService 缺少单元测试（后续按业务风险补充）
- 诊断数据依赖全部练习记录加载到内存，数据量大时可能有性能问题

### 下轮建议
- 可继续 Phase 15 深化：AI 学习建议（接入 AI 生成个性化建议）、错题归因分析增强
- 或补写 LearningDiagnosisService 单元测试
- 建议 commit message: `feat(diagnosis): Phase 15 P0 学习诊断与每日推荐`

---

## Round 74 - 2026-06-17

### 阶段
Phase 14：AI 可视化交互讲解 — P1 Mermaid 流程图渲染

### 本轮目标
为 VISUAL_INTERACTIVE 可视化讲解系统新增第 9 种可视化元素类型 `mermaid`，支持 Mermaid 流程图渲染，适用于算法流程、SQL 执行顺序、递归展开、条件分支等场景。

### 完成内容
1. **前端安装 `mermaid` 依赖**：`npm install mermaid`，支持 flowchart、sequence、gantt 等 Mermaid 图表语法。
2. **后端 `QuestionLearningAssetService.buildVisualInteractivePrompt` 增强**：Prompt 模板新增 `mermaid` 类型定义（字段为 `code` + 可选 `caption`），并添加 4 条规则引导 AI 在算法流程、SQL 执行顺序、递归展开等场景使用 mermaid 元素。
3. **前端 `ai.ts` 类型扩展**：新增 `VisualMermaidElement` 接口（type/label/code/caption），将其加入 `VisualElement` 联合类型。
4. **前端 `QuestionVisualInteractive.vue` 组件增强**：
   - 新增 mermaid 元素模板渲染（label + 容器 + 可选 caption）
   - Mermaid 延迟动态 import（`import('mermaid')`），仅在需要时加载，不影响首屏性能
   - `mermaid.render()` 异步渲染 SVG 到容器
   - Mermaid 语法错误时 fallback 为 `<pre>` 显示原始代码
   - 完善 mermaidRefs 管理和渲染状态追踪，避免重复渲染
5. **后端单元测试**：新增 `visualInteractivePromptContainsMermaidInstructions` 测试，验证 VISUAL_INTERACTIVE Prompt 包含 mermaid 类型定义、flowchart、SQL、Mermaid 语法规则等关键指令。
6. **ROADMAP.md 更新**：Phase 14 P1 Mermaid 流程图已完成。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/service/QuestionLearningAssetService.java`（修改：Prompt 增加 mermaid 类型定义和 4 条使用规则）
- `frontend/package.json`（修改：新增 mermaid 依赖）
- `frontend/src/api/ai.ts`（修改：新增 `VisualMermaidElement` 接口并加入 `VisualElement` 联合类型）
- `frontend/src/components/QuestionVisualInteractive.vue`（修改：新增 mermaid 模板/逻辑/CSS，动态 import，错误 fallback）
- `backend/src/test/java/com/learnplatform/service/QuestionLearningAssetServiceTest.java`（修改：新增 `visualInteractivePromptContainsMermaidInstructions` 测试）
- `docs/CHANGELOG_AGENT.md`（修改：新增 Round 74 记录）
- `docs/ROADMAP.md`（修改：Phase 14 P1 状态更新）
- `docs/HANDOFF.md`（修改：当前阶段更新）

### 验收结果
- [x] `cd backend && mvn compile`：编译成功
- [x] `cd backend && mvn test`：30 个测试全部通过（含新增 mermaid 测试）
- [x] `cd frontend && npx vue-tsc --noEmit`：TypeScript 检查通过，无错误
- [x] `cd frontend && npm test`：21 个测试文件、187 个测试全部通过
- [x] mermaid.js 动态 import 仅在需要时加载，不影响首屏性能
- [x] 语法错误 fallback 正常工作

### 遗留问题
- 候选方向：代码执行动画、SQL 执行顺序可视化、网络协议和操作系统过程可视化

### 下轮建议
- 继续 Phase 15：AI 学习画像与个性化推荐
- 或继续 Phase 14 候选方向
- 建议 commit message: `feat(ai): Phase 14 P1 Mermaid 流程图渲染（第 9 种可视化元素）`

---