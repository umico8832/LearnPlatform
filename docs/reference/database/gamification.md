# 学习激励数据

奖励是 `course_learning_event` 的派生投影，所有经验和成就都可以从事件和规则重建，不保存或宣称掌握度。

| 表 | 职责与约束 |
|---|---|
| `gamification_user_profile` | 用户每日目标、经验汇总和投影水位；用户行锁串行化奖励写入。 |
| `gamification_xp_ledger` | 一条已判分课程学习事件最多一条经验流水，`event_id` 唯一；同一课程内同一学习对象同日限制由 `(user_id, course_id, subject_type, subject_id, event_date)` 索引查询执行，并保存发奖前后经验快照供提交聚合稳定读取。 |
| `gamification_achievement` | 用户与成就编码唯一，保留真实触发事件和解锁时间。 |

自然日固定使用 `Asia/Shanghai`。既有 `course_learning_event.occurred_time` 是应用 JVM 的无时区 `DATETIME` 写入值；奖励投影先按该源时钟解释字段，再换算为上海自然日，不能把原字段直接当作上海本地时间。首次同一学习对象同日的已判分事件决定奖励结果，重复事件仍可作为原始学习事实存在，但不会获得经验、推进目标、连续日或连击。未判分的主观题不创建奖励流水。首次读取概况、成就、热力或新奖励时，用户行锁会按 `occurred_time, id` 只补算一次全部历史事实；后续每次作答只更新该用户的投影行和一条流水，显式补算可以安全重复调用。
