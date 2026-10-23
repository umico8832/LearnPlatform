# 架构决策记录

本目录保存对系统结构、关键质量属性或难以逆转方案有长期影响的决策。普通实现细节、临时 TODO 和每轮开发结果不写入 ADR。

## 状态

- `Proposed`：正在评估。
- `Accepted`：当前采用。
- `Superseded`：已被后续 ADR 替代。

## 记录

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](0001-frontend-backend-separation.md) | Accepted | 采用前后端分离架构 |
| [0002](0002-flyway-migrations.md) | Accepted | 使用 Flyway 管理数据库结构 |
| [0003](0003-ai-provider-abstraction.md) | Accepted | 通过 Provider 抽象隔离 AI 上游 |
| [0004](0004-observational-learning-effect.md) | Accepted | 学习效果统计只表达观察性关联 |
| [0005](0005-course-learning-state.md) | Accepted | AI 教学与试卷学习共享课程学习状态 |
| [0006](0006-separate-learner-admin-frontends.md) | Accepted | 用户学习端与管理系统采用独立前端应用目标 |
| [0007](0007-tutor-model-foundation.md) | Accepted | Tutor 模型契约、独立 AI 核心与原子调用准入 |
| [0008](0008-tutor-agent-execution-loop.md) | Accepted | Tutor Agent 可控工具循环、持久状态与恢复边界 |
| [0009](0009-learning-effort-gamification.md) | Accepted | 从真实学习事件派生投入激励与反馈 |
| [0010](0010-quiet-digital-textbook-motion.md) | Superseded | 使用 token 化 Quiet Digital Textbook 与 Vue/CSS 动效基线 |
| [0011](0011-living-textbook-spring-motion.md) | Accepted | 学习端重做为“会动的课本”视觉与弹簧动效基线 |
| [0012](0012-fsrs-review-scheduling.md) | Accepted | 复习排期改用 FSRS，并保留旧卡片到期日 |
| [0013](0013-new-york-business-time.md) | Accepted | 业务时间统一纽约，考试时长使用 UTC 起点 |
| [0014](0014-learner-dark-only.md) | Accepted | 学员端新页面只用深色 |

| [0015](0015-bounded-ai-execution.md) | Accepted | AI 使用有界准入，默认固定平台线程 |

ADR 采用追加式维护。决策变化时新增记录并标记替代关系，不重写旧决策的历史背景。
