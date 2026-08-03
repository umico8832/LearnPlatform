# 学习内容 API

## 课程与知识点

| 接口 | 说明 |
|---|---|
| `GET /api/courses` | 分页或筛选课程 |
| `GET /api/courses/list` | 获取课程简表 |
| `GET /api/courses/{id}` | 获取课程详情 |
| `GET /api/knowledge-points/tree/{courseId}` | 获取课程知识点树 |
| `POST /api/my-courses/{courseId}` | 将已开放课程幂等加入当前用户的个人课程库 |
| `GET /api/my-courses` | 查询当前用户的个人课程库 |
| `GET /api/my-courses/{courseId}/overview` | 查询已加入课程的学习概况与下一步候选目标 |
| `POST /api/my-courses/{courseId}/tutor-sessions` | 以已审查的课程知识点开始 Tutor 会话（必填查询参数 `knowledgePointId`） |
| `POST /api/my-courses/{courseId}/tutor-sessions/{sessionKey}/check` | 提交该会话的理解检查 `{ "optionId": "..." }` |

课程和知识点的写接口位于[管理与治理 API](admin-governance.md#课程与知识点管理)。
个人课程库关系以服务端认证用户为准，客户端不能指定或查询其他用户的 `userId`。
添加课程只建立学习入口，不表示已经开始学习或掌握课程内容。

课程总览仅对当前用户已加入的课程开放。它从已保存的课程学习事件、错题与到期复习计划聚合
`answeredCount`、`correctCount`、`dueReviewCount`、`unresolvedWrongCount` 和最近学习时间；
不会返回或推断“掌握度”。`recommendedTargets` 依次给出到期复习、未掌握错题和课程默认目录
的可执行入口，客户端只可按当前课程跳转到既有学习页面。

Tutor 仅可打开已加入课程、属于该课程且审查状态为 `REVIEWED` 的内容；响应不会返回正确选项。
理解检查由服务端首次判分并写入可追加课程学习事件，重复提交返回既有结果。

## 题库与纠错

| 接口 | 说明 |
|---|---|
| `GET /api/questions` | 按课程、知识点、题型等条件查询题目 |
| `GET /api/questions/{id}` | 获取用户可见的题目详情 |
| `POST /api/questions/{id}/correction-reports` | 提交题目纠错 |
| `GET /api/questions/correction-reports/my` | 查询我的纠错记录 |

用户题目详情不能泄露正确选项和标准解析。判分结果只能通过练习或考试提交接口获得。

## 收藏

| 接口 | 说明 |
|---|---|
| `POST /api/favorites/{questionId}` | 收藏题目 |
| `DELETE /api/favorites/{questionId}` | 取消收藏 |
| `GET /api/favorites/{questionId}/status` | 查询收藏状态 |
| `GET /api/favorites` | 查询收藏列表 |
| `GET /api/favorites/ids` | 查询收藏题目 ID 集合 |

## 评论

| 接口 | 说明 |
|---|---|
| `GET /api/comments/question/{questionId}` | 查询题目评论 |
| `POST /api/comments` | 发布评论 |
| `DELETE /api/comments/{commentId}` | 删除本人评论或执行管理删除 |
| `POST /api/comments/{commentId}/like` | 切换点赞状态 |
| `GET /api/comments/count/{questionId}` | 查询评论数量 |

## 用户投稿

| 接口 | 说明 |
|---|---|
| `POST /api/submission` | 提交题目投稿 |
| `GET /api/submission/my` | 查询我的投稿 |
| `GET /api/submission/{id}` | 查询本人可见的投稿详情 |

投稿不会自动成为正式题目，必须经过管理员审核和显式入库。

## 全局搜索

| 接口 | 说明 |
|---|---|
| `GET /api/search` | 搜索课程、知识点和题目 |
| `GET /api/search/suggestions` | 获取建议、历史和热门词 |
| `DELETE /api/search/history` | 清空当前用户搜索历史 |
| `DELETE /api/search/history/item` | 删除单条搜索历史 |
