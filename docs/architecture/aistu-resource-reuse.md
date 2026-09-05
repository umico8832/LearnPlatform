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

## Web 适配边界

- React 组件、Electron IPC、窗口生命周期和客户端本地持久化不属于共享协议；算法模型应先提取为
  可序列化 Schema、状态转换与测试向量，再适配 Vue。
- 固定课件只展示经过审查的数据和受限动作，不接受任意脚本、动态组件、公式执行或用户运行态。
- 学习路径使用稳定 `contentKey`，服务端返回可导航 ID 前必须校验课程归属与 `REVIEWED` 状态。
- 真实学习事实由服务端事件与判分产生；迁入目录、显示课件或完成前端操作本身不表示掌握。

## 迁移证据

当前内容与课件以[数据库迁移](../../backend/src/main/resources/db/migration/)和
[前端架构](frontend.md)为准；已有批次范围与受限课件说明保留在
[资源迁移历史](../project/changelog/2026-09.md#aistu-资源迁移历史快照)，不作为下一批开发任务。
