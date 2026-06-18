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