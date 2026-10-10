# ADR-0012：复习排期改用 FSRS，并保留旧卡片到期日

- 状态：Accepted
- 记录日期：2026-10-10
- 类型：学习数据与后端架构

## 背景

现有 `question_review_schedule` 保存 SM-2 简易因子、连续次数和日期；练习、复习、课程概览、Tutor 学习上下文及今日目标都读取这张表。旧记录只有最近一次质量和累计次数，无法还原每次作答时刻与评分，不能据此重建可信的 FSRS 记忆状态。M3 已将变式作答的 `assistance` 持久化为 `UNAIDED/AIDED`。

## 决策

- 使用 [Java-FSRS 1.0.0](https://central.sonatype.com/artifact/io.github.open-spaced-repetition/fsrs) 的 `Scheduler` 与 `Card`，采用官方默认权重和目标保持率；禁用随机 fuzzing，并禁用分钟级学习与重学步骤，使本站现有按日期呈现的复习计划可保持确定性。库内部使用 UTC；对外 `nextReviewDate` 仍按应用服务器时区由 `Card.due` 投影为日期。
- 继续以 `question_review_schedule` 的用户与题目唯一行为到期队列事实。V109 增加可空 FSRS 卡片 JSON 与最近评分；每次合格评分在锁定卡片行的事务中调用库、保存新状态，并更新现有到期日期、间隔、最近复习日期和次数。旧 SM-2 字段不再参与新排期，`easeFactor` 对已按 FSRS 评分的卡片置空，避免把旧因子当作新算法结果。
- V107 与空库均经 V108→V109 顺序升级。存量卡片保留原到期日和历史次数；FSRS 状态为空表示等待首次新评分。第一次合格独立作答从新的 `Card` 开始，不把 SM-2 `last_quality` 伪造成 FSRS 回顾记录。新加卡片在首次作答前也到期于当天。
- 只有明确独立的直接练习、题目理解原题、`UNAIDED` 变式和直接复习作答才形成 FSRS 评分事件。Tutor 引导练习、`AIDED` 变式、动画播放、提示领取、自评未判分的考试学习均不评分。评分事件按来源类型与来源 ID 唯一；作答、辅助标记、评分状态及课程学习事实处于原业务事务内。答错一律映射 `AGAIN`，答对默认 `GOOD`；直接复习的正确作答可由既有自评映射为 `HARD/GOOD/EASY`，错误作答不能被高自评改成成功评分。
- 错题列表增加最近一次选中错项的已审核误区及下次重做日期。没有当前有效误区或没有排期时返回 `null`，不推断错因；题目与审核内容被撤回时不展示过期讲解。课程概览与 Tutor 今日目标继续读取现有到期日期，因此 FSRS 更新后自动进入其到期复习入口。

## 备选与取舍

- 直接从旧 `last_quality`、`total_reviews` 推导 FSRS 稳定度：缺少完整评分序列和时间，结果不可追溯，因此不采用。
- 并行维护两套到期队列：会让错题、课程概览和复习页给出不同的重做时间，因此复用原表作为唯一日期投影。
- 保留 Java-FSRS 默认分钟级学习步骤：现有接口只表达日期，分钟级结果会在同一天过早显示为“到期”。本阶段禁用这些步骤；未来若产品需要日内复习，须先扩展精确时间与时区契约。

## 边界

FSRS 是排期算法，不代表掌握已被因果证明；学习效果统计继续遵循 [ADR-0004](0004-observational-learning-effect.md)。旧卡片的历史展示值保留，首次 FSRS 评分后新旧算法状态不混用。新的评分来源与 `assistance` 判定以[练习与复习 API](../../reference/api/practice-and-review.md)和[题目导师 API](../../reference/api/question-tutor.md)为准。

参考：[Java-FSRS 官方 README](https://github.com/open-spaced-repetition/java-fsrs#quickstart)（`reviewCard`、卡片 JSON、UTC 与可配置学习步骤）。
