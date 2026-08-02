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

ADR 采用追加式维护。决策变化时新增记录并标记替代关系，不重写旧决策的历史背景。
