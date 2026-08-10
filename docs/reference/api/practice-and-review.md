# 练习与复习 API

## 练习

| 接口 | 说明 |
|---|---|
| `GET /api/practice/questions` | 获取普通练习题 |
| `POST /api/practice/submit` | 提交单题答案并由后端判分 |
| `GET /api/practice/records` | 查询练习记录 |
| `GET /api/practice/stats` | 查询练习统计 |
| `GET /api/practice/wrong-questions` | 从练习入口获取错题 |
| `GET /api/practice/favorites` | 获取收藏题练习 |
| `GET /api/practice/adaptive` | 获取自适应练习题 |
| `GET /api/practice/adaptive/summary` | 获取自适应练习依据摘要 |

提交答案后，后端负责题型归一化、标准答案匹配、得分和错题同步。前端不得自行决定最终正确性。

## 错题本

| 接口 | 说明 |
|---|---|
| `GET /api/wrong-questions` | 查询错题；可用 `courseId`、`questionId` 和掌握状态筛选 |
| `GET /api/wrong-questions/stats` | 查询错题统计 |
| `PUT /api/wrong-questions/{id}/mastery` | 更新掌握状态 |
| `DELETE /api/wrong-questions/{id}` | 移出错题本 |

## 学习计划

| 接口 | 说明 |
|---|---|
| `GET /api/learning-plan` | 获取当前学习计划 |
| `PUT /api/learning-plan` | 创建或更新学习计划 |

## 间隔重复

| 接口 | 说明 |
|---|---|
| `GET /api/review/stats` | 获取复习统计 |
| `GET /api/review/due` | 获取到期复习项；可用 `courseId`、`questionId` 精确定位课程目标 |
| `GET /api/review/cards` | 获取复习卡片 |
| `POST /api/review/add/{questionId}` | 将题目加入复习 |
| `POST /api/review/sync-wrong-questions` | 同步错题到复习计划 |
| `POST /api/review/submit` | 提交复习质量并更新调度 |
| `DELETE /api/review/remove/{questionId}` | 移除复习题 |
| `POST /api/review/reset/{questionId}` | 重置题目复习进度 |
| `POST /api/review/ai-suggestion` | 获取 AI 复习建议 |
| `POST /api/review/ai-suggestion/stream` | 流式获取 AI 复习建议 |

从课程总览进入复习或错题时，客户端同时传递服务端“开始学习”返回的 `courseId` 和
`questionId`。两个条件按交集生效且只能读取当前认证用户的数据：到期复习会在数量限制前筛选，
错题会在数据库分页前筛选，因此返回记录和分页总数都属于目标课程；不传条件时保持原有全局入口。

## 学习统计与诊断

| 接口 | 说明 |
|---|---|
| `GET /api/statistics/overview` | 学习总览 |
| `GET /api/statistics/daily-trend` | 每日趋势 |
| `GET /api/statistics/course-stats` | 课程统计 |
| `GET /api/statistics/learning-report` | 学习报告 |
| `GET /api/statistics/learning-path` | 学习路径 |
| `GET /api/statistics/knowledge-graph` | 知识图谱数据 |
| `GET /api/statistics/learning-diagnosis` | 规则学习诊断 |
| `POST /api/statistics/ai-advice` | AI 个性化建议 |
| `POST /api/statistics/ai-advice/stream` | 流式 AI 个性化建议 |
| `GET /api/statistics/question-error-analysis` | 单题错因分析 |
| `GET /api/statistics/similar-questions` | 相似题推荐 |

统计和推荐结果属于学习辅助信息；观察性指标不能表述为因果效果。
