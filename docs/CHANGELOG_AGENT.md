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
- [x] `cd backend && mvn compile`：编译成功
- [x] `cd backend && mvn test`：180 个测试全部通过
- [x] `cd frontend && npx vue-tsc --noEmit`：TypeScript 检查通过，无错误
- [x] `cd frontend && npm test`：21 个测试文件、187 个测试全部通过
- [x] 后端 Prompt 设计覆盖 8 种可视化元素类型，含完整 JSON schema 和 AI 输出规则
- [x] 前端可视化组件支持 JSON 解析失败时自动回退为 Markdown 文本显示

### 遗留问题
- 可视化讲解依赖 AI 模型正确输出 JSON 格式，部分弱模型可能输出不规范的 JSON（前端已做 fallback 处理）
- 后续可考虑为 VISUAL_INTERACTIVE 补充后端单元测试（验证 Prompt 模板构建）
- Phase 14 后续迭代方向：Mermaid 语法渲染、代码执行动画、SQL 执行顺序可视化

### 下轮建议
- 可为 VISUAL_INTERACTIVE Prompt 效果做实际调优（根据真实 AI 输出质量调整 Prompt 指令）
- 可进入 Phase 14 P1（Mermaid 流程图渲染）或 Phase 15（AI 学习画像与个性化推荐）
- 建议 commit message: `feat(ai): 实现 Phase 14 可视化交互讲解（结构化 JSON 渲染 + 8 种可视化元素）`

---