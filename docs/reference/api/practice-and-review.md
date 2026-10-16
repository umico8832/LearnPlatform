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

M4 在错题列表每条记录增加 `misconception` 与 `nextReviewDate`。`misconception` 为最近一次所选错项当前有效的已审核误区 `{title, explanation, source: {kind, reviewStatus}}`，其中 `kind=CURATED|AI_GENERATED`、公开 `reviewStatus=REVIEWED`；无有效讲解时为 `null`。它只解释有对应选项讲解的作答，不根据题目或统计猜测错因。`nextReviewDate` 是该用户同题现有复习卡片的到期日期，未排期为 `null`。分页、筛选与用户隔离保持原约定；这两个字段随每页记录返回，统计接口暂不扩展。

| 接口                                    | 说明                                                                       |
| --------------------------------------- | -------------------------------------------------------------------------- |
| `GET /api/wrong-questions`              | 查询错题；可用 `courseId`、`questionId`、`knowledgePointId` 和掌握状态筛选 |
| `GET /api/wrong-questions/stats`        | 查询错题统计                                                               |
| `PUT /api/wrong-questions/{id}/mastery` | 更新掌握状态                                                               |
| `DELETE /api/wrong-questions/{id}`      | 移出错题本                                                                 |

## 间隔重复

M4 的排期采用 [ADR-0012](../../architecture/decisions/0012-fsrs-review-scheduling.md) 的 FSRS。现有路径和 `nextReviewDate`、`intervalDays`、`lastReviewDate`、`totalReviews` 字段保留：新评分后的日期与间隔由 FSRS 卡片产生；存量未评分卡片先显示迁移前原到期日期。`easeFactor` 对 FSRS 卡片为 `null`，仅作为存量兼容字段；`repetitions` 和 `lastQuality` 是展示投影，不作为 FSRS 计算输入。旧统计字段 `avgEaseFactor` 仅对仍有旧因子的卡片求平均，无此类卡片时为 `0`，不表示 FSRS 掌握水平。前端不得从这些旧字段推算排期。

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
