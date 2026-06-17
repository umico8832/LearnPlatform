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
- 每日建议目前为规则生成，后续可接入 AI 生成更个性化的建议文本
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
- `docs/ROADMAP.md`（修改：Phase 14 P1 状态更新）
- `docs/CHANGELOG_AGENT.md`（修改：新增 Round 74 记录）

### 验收结果
- [x] `cd backend && mvn test`：30 个测试全部通过（含新增 mermaid Prompt 测试）
- [x] `cd frontend && npx vue-tsc --noEmit`：TypeScript 检查通过，无错误
- [x] `cd frontend && npm test`：21 个测试文件、187 个测试全部通过
- [x] mermaid 为动态 import，不影响首屏加载性能
- [x] Mermaid 渲染失败时自动 fallback 为代码显示

### 遗留问题
- Mermaid 图表的实际渲染效果依赖 AI 模型输出合法的 Mermaid 语法
- 后续可继续迭代 Phase 14 候选方向：代码执行动画、SQL 执行顺序可视化（可用 mermaid sequenceDiagram 增强）

### 下轮建议
- 可进入 Phase 15（AI 学习画像与个性化推荐）
- 或继续 Phase 14 深化：代码执行动画、SQL 执行顺序可视化
- 建议 commit message: `feat(ai): Phase 14 P1 Mermaid 流程图渲染（第 9 种可视化元素）`

---

## Round 73 - 2026-06-17

### 阶段
Phase 14：AI 可视化交互讲解 — P0 文本可视化

### 本轮目标
实现 Phase 14 P0 核心能力：为算法、数据结构、DP 等过程类题目提供结构化可视化讲解，用图表、数组状态、树、矩阵等可视化元素替代纯 Markdown 文本。

### 完成内容
1. **后端 `AiAssetType` 新增 `VISUAL_INTERACTIVE`**：第 7 种资产类型，标签为"可视化讲解"。
2. **后端 `QuestionLearningAssetService` 新增 `buildVisualInteractivePrompt`**：精心设计的结构化 Prompt，强制 AI 输出标准 JSON（非 Markdown），支持 8 种可视化元素类型：
   - `text` — 普通文本/Markdown
   - `table` — 二维数据表格
   - `step_list` — 带状态的步骤列表
   - `state_array` — 数组/一维状态展示（排序/搜索过程）
   - `matrix` — 二维矩阵/DP 填表
   - `tree` — 递归调用树/树结构
   - `bar_chart` — 横向柱状图对比
   - `number_line` — 数轴/指针位置
3. **前端 `ai.ts` 类型扩展**：新增 `VISUAL_INTERACTIVE` 到 `AiAssetType` 联合类型和标签映射；新增完整的可视化数据类型定义（`VisualElement`、`VisualInteractiveData` 等 15 个类型接口）。
4. **前端 `QuestionVisualInteractive.vue` 组件**：纯 CSS（零额外依赖）可视化渲染组件，包含：
   - JSON 安全解析（直接解析 + 代码块提取双重策略）
   - 解析失败时自动 fallback 为 Markdown 文本显示
   - 8 个可视化元素渲染器（table/matrix 样式化表格、state_array 带状态着色、递归 tree 节点、bar_chart 纯 CSS 柱状图、number_line 数轴、step_list 状态步骤列表）
   - 响应式设计
5. **前端 `QuestionLearningAsset.vue` 集成**：新增第 7 个 Tab「📊 可视化讲解」（放在常见误区和变式题之间），使用 `QuestionVisualInteractive` 组件渲染可视化内容（流式加载和缓存展示均支持）。
6. **ROADMAP.md 更新**：Phase 14 状态更新为 🚧 开发中，目标清单和可视化元素类型列表已写入。

### 修改文件清单
- `backend/src/main/java/com/learnplatform/dto/AiAssetType.java`（修改：新增 VISUAL_INTERACTIVE 枚举值）
- `backend/src/main/java/com/learnplatform/service/QuestionLearningAssetService.java`（修改：switch 新增 VISUAL_INTERACTIVE 分支 + `buildVisualInteractivePrompt` 方法）
- `frontend/src/api/ai.ts`（修改：新增 VISUAL_INTERACTIVE 类型 + 可视化数据类型定义 15 个接口）
- `frontend/src/components/QuestionVisualInteractive.vue`（新增：可视化渲染组件）
- `frontend/src/components/QuestionLearningAsset.vue`（修改：import 新组件 + 新增 assetTabs 第 7 项 + reactive 状态扩展 + template 中条件渲染）
- `docs/ROADMAP.md`（修改：Phase 14 状态从 ⏳ 规划中 更新为 🚧 开发中，新增 P0 目标清单）

### 验收结果
- [x] `cd backend && mvn test`：30 个测试全部通过
- [x] `cd frontend && npx vue-tsc --noEmit`：TypeScript 检查通过，无错误
- [x] `cd frontend && npm test`：21 个测试文件、187 个测试全部通过
- [x] 8 种可视化元素类型均有独立渲染逻辑
- [x] JSON 解析失败时自动 fallback 为 Markdown 显示

### 遗留问题
- 可视化讲解的实际效果依赖 AI 模型输出符合 JSON 格式的数据
- 后续可继续迭代：Mermaid 流程图渲染、代码执行动画、SQL 执行顺序可视化

### 下轮建议
- Phase 14 P1：新增 mermaid 流程图渲染
- 或进入 Phase 15（AI 学习画像与个性化推荐）