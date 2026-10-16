# 练习与复习 API

## 练习

| 接口                                 | 说明                     |
| ------------------------------------ | ------------------------ |
| `GET /api/practice/questions`        | 获取普通练习题           |
| `POST /api/practice/submit`          | 提交单题答案并由后端判分 |
| `GET /api/practice/records`          | 查询练习记录             |
| `GET /api/practice/stats`            | 查询练习统计             |
| `GET /api/practice/wrong-questions`  | 从练习入口获取错题       |
| `GET /api/practice/favorites`        | 获取收藏题练习           |
| `GET /api/practice/adaptive`         | 获取自适应练习题         |
| `GET /api/practice/adaptive/summary` | 获取自适应练习依据摘要   |

提交答案后，后端负责题型归一化、标准答案匹配、得分和错题同步。前端不得自行决定最终正确性。

普通、自适应、错题和收藏练习仅抽取具有可用自动判分依据的题目，过滤先于数量限制；
题目的启用、可见性和所有者限制继续生效。单选题需要且只能有一个正确选项标签，多选题至少需要
一个正确选项标签；判断题需要且只能有一个正确答案，接受 `TRUE`/`FALSE`、正确/错误、对/错、A/B
并规范为 `TRUE` 或 `FALSE`。填空和简答需要已配置的正确答案内容；已删除选项不参与判断。带有效关键词的
简答题继续沿用现有匹配规则。
直接提交时也会重新核对判分依据，缺失时返回业务错误并保留客户端草稿，不写入练习、错题、
学习事件或经验数据。题库阅读和正式考试的人工批阅流程不受此筛选影响。

智能推荐只把非空且已判定正误的练习记录作为难度依据。少于 5 次有效作答时从难度 1–3 起步；
达到 3 次后依据实际正确率在该范围内调整首题难度，之后继续按各难度的历史表现分配题量。
抽题配额按权重的最大余数分配，单题请求也落在当前最高权重难度；起步阶段一组题按推荐权重排序，
同难度内随机。若筛选范围内基础题不足，才从其他可自动判分的难度补足，仍可能返回较难题。
推荐摘要在零记录时返回起步难度偏好；具体题目仍取决于当前筛选范围的可用题库。

## 错题本

错题列表每条记录还返回布尔 `hasUnderstanding`，为 `true` 时“重做”可进入 `/questions/{questionId}`，否则沿用旧练习入口；判定与空值规则见[理解入口契约](question-understanding.md#列表中的理解入口b7)。

M4 在错题列表每条记录增加 `misconception` 与 `nextReviewDate`。`misconception` 为最近一次所选错项当前有效的已审核误区 `{title, explanation, source: {kind, reviewStatus}}`，其中 `kind=CURATED|AI_GENERATED`、公开 `reviewStatus=REVIEWED`；无有效讲解时为 `null`。它只解释有对应选项讲解的作答，不根据题目或统计猜测错因。`nextReviewDate` 是该用户同题现有复习卡片的到期日期，未排期为 `null`。这两个字段随每页记录返回。

| 接口                                    | 说明                                                                       |
| --------------------------------------- | -------------------------------------------------------------------------- |
| `GET /api/wrong-questions`              | 查询错题；可用 `courseId`、`questionId`、`knowledgePointId` 和掌握状态筛选 |
| `GET /api/wrong-questions/stats`        | 查询错题统计                                                               |
| `PUT /api/wrong-questions/{id}/mastery` | 更新掌握状态                                                               |
| `DELETE /api/wrong-questions/{id}`      | 移出错题本                                                                 |

### 到期筛选、排序与范围统计（B8）

`GET /api/wrong-questions` 保留 `pageNum=1`、`pageSize=10`，接受以下可选参数：

| 参数 | 类型 / 默认值 | 语义 |
| --- | --- | --- |
| `courseId`、`questionId`、`knowledgePointId` | 整数 | 课程、题目、关联知识点，按交集限定范围 |
| `masteryLevel` | 整数 | 原掌握状态筛选 |
| `dueOnly` | 布尔，默认 `false` | `true` 时只返回当前用户有效排期的 `nextReviewDate <= 今天`，包含逾期；未排期和已移除卡片不计入 |
| `sortBy` | `updateTime`（默认）或 `nextReviewDate` | 默认按更新时间降序；后者按下次重做日期升序，未排期排最后；同日期按更新时间降序、再按错题 ID 降序 |

所有条件在数据库分页前组合生效，响应仍为统一 `R<Page<WrongQuestionVO>>`，`total` 是满足全部条件的错题总数。
无效 `sortBy` 返回参数校验错误（`1001`）。示例：
`GET /api/wrong-questions?courseId=1&knowledgePointId=2&masteryLevel=0&dueOnly=true&sortBy=nextReviewDate&pageNum=1&pageSize=10`。

`GET /api/wrong-questions/stats` 新增可选 `courseId`、`questionId`、`knowledgePointId`，与列表的范围条件按相同交集生效；
返回统一 `R`，数据保留 `total`、`unmastered`、`partial`、`mastered`、`courseWrongCount`，新增整数 `dueCount`（包含逾期）。
各计数及课程分布均只统计当前用户、当前范围中未移出的错题；不传参数仍统计全局错题。统计不接受 `masteryLevel`、`dueOnly` 或排序参数，
以便页面同时显示当前范围的掌握状态分布与到期数量。

“今天”使用应用服务器默认时区的当前日期（Java `LocalDate.now()`），与复习到期判断及
[ADR-0012](../../architecture/decisions/0012-fsrs-review-scheduling.md) 的日期投影一致；不使用数据库会话的 `CURRENT_DATE`，也不采用浏览器时区。
错题到期只表达该错题现有排期是否到期，不额外套用复习入口的自动判分可用性筛选。

## 间隔重复

M4 的排期采用 [ADR-0012](../../architecture/decisions/0012-fsrs-review-scheduling.md) 的 FSRS。现有路径和 `nextReviewDate`、`intervalDays`、`lastReviewDate`、`totalReviews` 字段保留：新评分后的日期与间隔由 FSRS 卡片产生；存量未评分卡片先显示迁移前原到期日期。`easeFactor` 对 FSRS 卡片为 `null`，仅作为存量兼容字段；`repetitions` 和 `lastQuality` 是展示投影，不作为 FSRS 计算输入。前端不得从这些旧字段推算排期。

评分仅在真实、无提示的首次作答判分成功后发生：`POST /api/practice/submit` 的直接练习、题目理解原题（复用同一 `practice_record`）、`POST /api/review/submit` 的直接复习以及 `assistance=UNAIDED` 的变式。`AIDED` 变式仍保留判分与课程学习事件，但没有 FSRS 评分事件；Tutor 引导练习等无法证实独立性的入口也不评分。评分事件以实际作答记录 ID 加来源类型去重，同一事务内持久化，重试不重复推进卡片。

评分映射：答错一律 `AGAIN`；答对默认 `GOOD`。直接复习的答对且 `selfAssessedQuality` 为 `0..3` 时用 `HARD`，为 `4` 时用 `GOOD`，为 `5` 时用 `EASY`；答错时自评不能覆盖 `AGAIN`。`answerTime` 不参与评级。未作答、无自动判分依据、权限不符或事务失败均不创建评分事件。

卡片仍由 `/api/review/add/{questionId}`、错题同步和既有答错路径加入；新卡首次评分前当日到期。`/api/review/submit` 在同一用户与题目卡片锁下判分和更新 FSRS 状态，并返回更新后的卡片。重置清空 FSRS 记忆状态并让卡片当日到期；移出不删除历史评分记录，恢复卡片沿用移出前的排期。`GET /api/review/due` 与课程今日学习目标仍以同一 `nextReviewDate` 判断到期，数量限制前维持现有可判分过滤。

| 接口                                     | 说明                                                           |
| ---------------------------------------- | -------------------------------------------------------------- |
| `GET /api/review/stats`                  | 获取复习统计                                                   |
| `GET /api/review/due`                    | 获取到期复习项；可用 `courseId`、`questionId` 精确定位课程目标 |
| `GET /api/review/cards`                  | 获取复习卡片                                                   |
| `POST /api/review/add/{questionId}`      | 将题目加入复习                                                 |
| `POST /api/review/sync-wrong-questions`  | 同步错题到复习计划                                             |
| `POST /api/review/submit`                | 提交复习质量并更新调度                                         |
| `DELETE /api/review/remove/{questionId}` | 移除复习题                                                     |
| `POST /api/review/reset/{questionId}`    | 重置题目复习进度                                               |
| `POST /api/review/ai-suggestion`         | 获取 AI 复习建议                                               |
| `POST /api/review/ai-suggestion/stream`  | 流式获取 AI 复习建议                                           |

复习作答同样在写入前核对当前题目访问与自动判分依据，拒绝时保留答案且不推进复习调度。到期队列在数量限制前
排除缺少依据的题目，到期/逾期计数和 AI 待复习建议保持同一口径。全部卡片仍保留这些历史卡，
通过 `availableForReview: false` 告知客户端等待配置答案；答案补齐后自动恢复可用性。
卡片总量、历史分类和已复习计数保持真实历史，本规则不会追溯改写判分、经验或复习进度。

### 复习卡片的理解入口（B17）

`GET /api/review/due`、`GET /api/review/cards` 的每张卡片，以及 `POST /api/review/submit`
返回的更新后卡片，均包含必返布尔 `hasUnderstanding`，无可用理解内容时为 `false`，不返回 `null`。
为 `true` 时，复习页可进入 `/questions/{questionId}`；为 `false` 时沿用单题练习入口，
能否直接复习仍读取既有 `availableForReview`。

该字段复用错题列表和题目列表的[理解入口门禁](question-understanding.md#列表中的理解入口b7)，
按当前认证用户判定，管理员普通复习接口也不放行预览内容。最新理解版本待审核、撤回或没有理解内容时为 `false`；
题目本身为 `DRAFT` 的卡片仍按既有规则从列表排除。历史卡片不能绕过当前题目及课程访问规则。
列表批量判定，不逐卡读取理解内容；原有筛选、限量、排序、排期和用户隔离口径保持不变。
此字段只表达读取时刻的可用性，进入理解页后仍重新校验，内容变化时沿用既有错误码。

### 复习统计与卡片状态（B14）

`GET /api/review/stats` 返回 `R<ReviewStatsVO>`，只统计当前认证用户未移出的非草稿卡片，无范围参数。
所有字段均为非空整数，空计划全部为 `0`。卡片分类保留暂不可自动判分的历史卡；到期项另按可作答条件过滤。

| 字段 | 学习者称谓 / 一句话含义 | 计算依据 |
| --- | --- | --- |
| `totalCards` | 复习卡片：当前复习计划中保留的题目数。 | 有效卡片总数 |
| `dueToday` | 到期可复习：今天可以开始作答的到期卡片数，包含逾期。 | `nextReviewDate <= 今天` 且通过复习入口自动判分可用性筛选 |
| `overdue` | 逾期可复习：原定日期早于今天且可以作答的卡片数。 | 同上，但日期严格小于今天，是 `dueToday` 的子集 |
| `reviewedToday` | 最近评分在今天：当前卡片中最近一次评分日期为今天的题目数。 | `lastReviewDate = 今天` 且 `totalReviews > 0`；包含独立练习、原题、无提示变式、直接复习及保留的旧评分日期，每题最多一张卡；不是今日作答次数或待办完成量 |
| `pendingRatingCards` | 待首次 FSRS 评分：尚未建立 FSRS 状态的卡片数。 | `fsrs_card_json IS NULL`，包括新加入、重置和等待首次 FSRS 评分的旧卡；旧卡可能已有历史复习次数 |
| `learningCards` | 初学排期：FSRS 当前处于初学状态的卡片数。 | 卡片 JSON 中 `state = LEARNING` |
| `reviewCards` | 间隔复习：FSRS 当前处于常规间隔排期的卡片数。 | 卡片 JSON 中 `state = REVIEW`；不按间隔长短判断掌握 |
| `relearningCards` | 重学排期：FSRS 当前处于重学状态的卡片数。 | 卡片 JSON 中 `state = RELEARNING` |
| `lastAgainCards` | 最近评分答错：最近一次 FSRS 评分为答错的卡片数。 | 已有 FSRS 卡片且 `fsrs_last_rating = AGAIN`，所有评分入口的答错均映射为 AGAIN |
| `lastHardCards` | 最近自评困难：最近一次 FSRS 评分为答对但自评困难的卡片数。 | 已有 FSRS 卡片且 `fsrs_last_rating = HARD`，来自直接复习答对且自评 `0..3` |

四个排期状态计数之和为 `totalCards`。最近评分两项可以与任一已评分排期状态重叠，互相不重叠；“最近”指最近一次合格评分，不设时间窗口，后续辅助作答不覆盖它。
旧卡没有 FSRS 评分时不从 SM-2 因子、间隔、质量或历史次数推断状态或最近 FSRS 评分；原到期日保持不变，首次独立评分后自然进入 FSRS 状态。
当前配置禁用日内初学/重学步骤，因此 `learningCards`、`relearningCards` 通常为 `0`；即使答错，卡片也可能处于 `REVIEW`，答错事实应读取 `lastAgainCards`。
这些字段表达排期或评分事实，均不证明掌握或长期稳定。

`GET /api/review/cards`、`/due` 和提交响应的卡片增加非空 `schedulingState`（`UNRATED | LEARNING | REVIEW | RELEARNING`）及可空 `lastRating`（`AGAIN | HARD | GOOD | EASY`，无 FSRS 评分为 `null`）。
`statusLabel` 按同一状态依次返回“待首次 FSRS 评分 / 初学排期 / 间隔复习 / 重学排期”；最近答错、自评困难独立于该标签。
列表暂没有类别筛选参数。客户端如做本地类别筛选，须用 `schedulingState` 或 `lastRating` 与上表对应，不得再按间隔或简易因子分桶。
`/cards?courseId=` 的列表为课程子集，不能与无范围参数的全局统计直接比较；`/due` 仍在限量前组合课程、题目和知识点条件。

B14 删除旧统计字段 `newCards`、`masteredCards`、`difficultCards`、`avgEaseFactor`、`streakDays`，不保留别名。
调用方分别改用 `pendingRatingCards`、`reviewCards`、`lastAgainCards/lastHardCards`；后两项没有替代字段，历史平均因子与按卡片最近日期推断的连续天数不再展示。
保留的 `learningCards` 改为 FSRS LEARNING 状态计数；旧前端必须同步字段与称谓，C6 只使用本节口径。

“今天”使用应用服务器默认时区的 `LocalDate.now()`。日期为空的卡片不计入到期或今日评分，但仍计入总量和排期状态。
错题页 `nextReviewDate` 和课程页 `dueReviewCount` 读取同一卡片日期：课程待办是课程内、可自动判分、今天及以前到期的子集，与复习 `dueToday` 使用相同到期规则；一道题关联多个知识点时各点可重复计数，不能直接相加。
错题 `dueCount` 只统计错题范围中的到期卡，包含暂不可判分卡，故不必等于复习 `dueToday` 或课程 `dueReviewCount`；错题的人工掌握状态也独立于 FSRS 状态。

从课程总览进入复习或错题时，客户端同时传递服务端“开始学习”返回的 `courseId` 和
`questionId`。两个条件按交集生效且只能读取当前认证用户的数据：到期复习会在数量限制前筛选，
错题会在数据库分页前筛选，因此返回记录和分页总数都属于目标课程；不传条件时保持原有全局入口。
错题接口还接受课程内 `knowledgePointId`：按该知识点关联的题目在数据库分页前筛选，可与课程、
题目条件组合；测评复盘的知识点汇总对存在错题的知识点提供带 `knowledgePointId` 与
`knowledgePointName` 的深链，页面以可清除的知识点筛选标记展示，筛选只呈现真实错题事实。

## 学习统计与诊断

| 接口                                          | 说明               |
| --------------------------------------------- | ------------------ |
| `GET /api/statistics/overview`                | 学习总览           |
| `GET /api/statistics/daily-trend`             | 每日趋势           |
| `GET /api/statistics/course-stats`            | 课程统计           |
| `GET /api/statistics/learning-report`         | 学习报告           |
| `GET /api/statistics/learning-path`           | 学习路径           |
| `GET /api/statistics/knowledge-graph`         | 知识图谱数据       |
| `GET /api/statistics/learning-diagnosis`      | 规则学习诊断       |
| `POST /api/statistics/ai-advice`              | AI 个性化建议      |
| `POST /api/statistics/ai-advice/stream`       | 流式 AI 个性化建议 |
| `GET /api/statistics/question-error-analysis` | 单题错因分析       |
| `GET /api/statistics/similar-questions`       | 相似题推荐         |

统计和推荐结果属于学习辅助信息；观察性指标不能表述为因果效果。

规则诊断中的 `totalPractice`、`totalAttempts` 表示已记录的作答次数，包含同一题目的重复作答。
知识点仅有一次作答时，`masteryStatus` 为 `INSUFFICIENT_DATA`，保留本次结果和后续练习入口，
不据此认定掌握或薄弱；课程的薄弱知识点汇总遵循同一边界。近 30 天只有零或一个活跃日时，
`frequencyLevel` 同样为 `INSUFFICIENT_DATA`；频次文案只描述平台记录，不假设帐号年龄或长期习惯。
页面、规则建议和 AI 输入同步这一证据范围；未作答内容显示“暂无记录”，实际答错后的 0% 继续保留。

诊断推荐与相似题用于直接练习，候选在推荐限额之前须满足自动判分依据要求；历史作答和错题事实保留。
相似题在评分前还需要语义锚点：来源题有关联知识点时，候选须共享至少一个知识点；
来源题没有知识点时，候选须属于同一课程；来源题同时没有知识点和课程时不返回候选。
重新配置有效答案后，题目可再次参与推荐；原有可见性范围、推荐优先级和相似度评分保持不变。
正式题创建、修改或删除成功提交后清除诊断缓存，答案配置变化在下一次读取时生效；回滚保留原缓存。
反复错题的汇总和明细均按累计错答至少 2 次计算；汇总统计所有符合条件的错题本条目，明细按错次降序最多展示 10 条。
