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
题目的启用、可见性和所有者限制继续生效。单选、多选需要正确选项标签，判断、填空和简答需要
已配置的正确答案内容；已删除选项不参与判断。带有效关键词的简答题继续沿用现有匹配规则。
直接提交时也会重新核对判分依据，缺失时返回业务错误并保留客户端草稿，不写入练习、错题、
学习事件或经验数据。题库阅读和正式考试的人工批阅流程不受此筛选影响。

## 错题本

| 接口                                    | 说明                                                                       |
| --------------------------------------- | -------------------------------------------------------------------------- |
| `GET /api/wrong-questions`              | 查询错题；可用 `courseId`、`questionId`、`knowledgePointId` 和掌握状态筛选 |
| `GET /api/wrong-questions/stats`        | 查询错题统计                                                               |
| `PUT /api/wrong-questions/{id}/mastery` | 更新掌握状态                                                               |
| `DELETE /api/wrong-questions/{id}`      | 移出错题本                                                                 |

## 间隔重复

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

复习作答同样在写入前核对自动判分依据，拒绝时保留答案且不推进复习调度。到期队列在数量限制前
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
重新配置有效答案后，题目可再次参与推荐；原有可见性范围、推荐优先级和相似度评分保持不变。
正式题创建、修改或删除成功提交后清除诊断缓存，答案配置变化在下一次读取时生效；回滚保留原缓存。
反复错题的汇总和明细均按累计错答至少 2 次计算；汇总统计所有符合条件的错题本条目，明细按错次降序最多展示 10 条。
