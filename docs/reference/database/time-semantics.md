# 时间存储语义

业务时区采用 `America/New_York`，决策见 [ADR-0013](../../architecture/decisions/0013-new-york-business-time.md)。
Java 本地日期时间读写显式使用统一业务时钟；JDBC 连接与 MySQL 会话使用该命名时区，Jackson 配置同口径。
MySQL 时区表必须包含 `Asia/Shanghai`、`America/New_York`；迁移缺少规则即失败，不回退固定偏移。

## 存量分类与迁移

V118 对 V117 业务表的全部 `DATETIME` 列一次性执行 `CONVERT_TZ(value,'Asia/Shanghai','America/New_York')`，
包括创建/更新时间、作答、考试起止、审核、过期与租约、学习事件、成就解锁和 AI 调用时间。空值与精度保留，
同一行时间列一起更新，避免 `ON UPDATE` 把历史更新时间替换为迁移时刻。冬季和夏季由 MySQL 时区规则分别换算。
按用户决定将历史无偏移业务值视为北京时间；旧运行机器曾以其他时区写入的数据无法仅凭字段恢复原始时刻。

例外：

- `fsrs_review_event.reviewed_at` 原已按 UTC 写入，保持不变；`fsrs_card_json` 内的绝对时间保持不变。
- `exam_record.start_instant_utc` 新增为 UTC 起点，由旧 `start_time` 按北京时间换算为 UTC，供精确时长与回拨重叠小时使用。
- Flyway 的 `installed_on` 为迁移系统 `TIMESTAMP`，保持原时刻；不对 `TIMESTAMP` 二次平移。
- 所有原有 `DATE` 是无时刻日期：复习排期与上次复习、学习计划、AI 资源查看分桶保留日期标签。
  后续生成日期均按纽约自然日计算；保留旧排期避免凭空提前或推迟已有复习安排。
- `gamification_xp_ledger.event_date` 是事件派生日期，由换算后 `occurred_time` 重算；
  用户聚合的 `last_answer_date`、当前和最长连续日从已有正经验流水的新日期重算。
  已获得的经验、连击、成就及历史同日去重结果保留，不重新发奖或撤奖。

迁移只由 Flyway 执行；验收使用隔离 Testcontainers。日常开发库不手工更新、不清数据。
迁移上线需停止旧应用写入，再迁移并启动新版本，避免新旧本地时间语义混写。
