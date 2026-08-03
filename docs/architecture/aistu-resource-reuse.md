# AiStu 教学资源复用边界

LearnPlatform 的 Phase 23 使用 AiStu 已有的 408 数据结构知识、Tutor 协议和互动课件
作为首个 Web 课程闭环的输入，但不把 Electron 桌面架构或客户端本地状态直接迁入
服务端。

## 资源盘点

| AiStu 资源 | 当前规模或职责 | LearnPlatform 复用方式 |
|---|---|---|
| `content/ods-material/knowledge_base` | 408 课程 taxonomy、原子知识、关系、来源、RAG chunks 和质量状态 | 保留稳定 ID、来源与质量状态，分批导入课程内容域 |
| `packages/contracts` | 课程范围、误解、课件 session 和结构化互动事件 | 作为 Web 学习事件契约的语义输入，不照搬 IPC 外层 |
| `packages/tutor-runtime` | Tutor 命令归一化与教学编排 | 迁移可验证的教学决策，不依赖 Electron 或本地 Provider 边界 |
| `packages/lessons` | 已注册 React 互动课件及专属 Schema | 保留教学目标、步骤、预测和算法模型，Web 展示层适配 Vue |
| `courseLearningStore` | 本地知识接触、课件完成、预测错误、误解和复盘原型 | 只参考事件语义；服务端统一学习状态不能由 Zustand 快照充当 |

## 稳定标识与来源

- AiStu 的 `cs408-data-structures`、`408-*` 和 `cs408-*` 标识进入 LearnPlatform
  的 `content_key`，数据库自增 ID 继续作为内部主键。
- `content_source = AISTU` 只说明迁移来源，不自动表示内容已审核、已发布或适合直接
  进入正式教学。
- 原子知识、RAG chunks 和课件迁入前必须保留对应来源、版本、质量状态与适用许可；
  `review_pending` 不能在 Web 中展示为已审核内容。
- LearnPlatform 运行时不能依赖相邻 AiStu 工作区的绝对路径。资源需要通过明确迁移或
  构建产物进入本仓库，避免两个项目工作树形成隐式部署依赖。

## 实现边界

V21 首先建立稳定内容键和 `user_course` 个人课程关系，并迁入 408 数据结构课程及
8 个顶层章节。该迁移只建立真实课程目录入口，不表示 122 个原子知识点、676 个
chunks 或 10 个课件已经迁入 Web。

后续顺序是：

1. 定义服务端课程学习事件、幂等键、来源和版本；
2. 将现有练习、错题、复习、考试和 AI 观察事件映射到统一语义；
3. 选择完成审查的最小知识与课件切片，并迁移框架无关模型；
4. 实现课程总览和“开始学习”的候选目标选择；
5. Web 闭环稳定后，再决定桌面端是否复用同一服务协议。

React 组件、Electron IPC、窗口生命周期和本地持久化不属于共享协议。若课件算法模型
可独立于 React，应先提取可序列化 Schema、状态转换和测试向量，再用 Vue 实现 Web
展示层，避免维护两套不一致的教学逻辑。
