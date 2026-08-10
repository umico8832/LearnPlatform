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
不会返回或推断“掌握度”。`tutorProgress` 只列出当前课程中已审查、已迁入的 Tutor 内容，并以
`NOT_STARTED`、`IN_PROGRESS`、`COMPLETED` 表示是否存在会话或首次正确理解检查；未迁入的目录节点
不会被列为未完成。`recommendedTargets` 依次给出当前课程中已审查且尚未完成理解检查的 Tutor 内容、
到期复习、未掌握错题和课程默认目录。Tutor 目标携带当前课程内的
`knowledgePointId`，客户端只能跳转到既有 Tutor 页面；一次正确理解检查后，该内容不再作为
未完成教学推荐。

Tutor 仅可打开已加入课程、属于该课程且审查状态为 `REVIEWED` 的内容；响应不会返回正确选项。
理解检查由服务端首次判分并写入可追加课程学习事件，重复提交返回既有结果。
当已审查教学内容显式声明路径时，理解检查响应会额外给出 `guidanceType`、
`guidanceTitle`、`guidanceDescription` 和可空的 `guidanceKnowledgePointId`：答错时为前置补充
（`PREREQUISITE`），答对时为后续目标（`NEXT_TARGET`）。前三项只表达路径建议；服务端仅在目标
属于当前课程且知识点状态为 `REVIEWED` 时返回可导航 ID，客户端不能用内容中的任意标识绕过课程
与审查边界。

已审查 Tutor 内容在 `lesson.visualization` 中使用受限的
`ARRAY_STACK_INSERTION` v1 Schema：`capacity`（1–12）、`initialElements`（非空字符串数组，
长度小于 capacity）、`insertIndex` 与 `insertValue`。客户端仅对通过该 Schema 校验的固定类型
渲染器进行回放。`ARRAY_STACK_RESIZE` v1 Schema 使用 `previousCapacity`（1–12）和与其长度
相等的非空 `initialElements`，固定渲染器据此展示 `max(1, 2n)` 的新数组和按序复制。
`ARRAY_QUEUE_REPRESENTATION` v1 Schema 使用 `capacity`（2–12）、`headIndex`（合法物理下标）和
长度小于 capacity 的非空 `elements`；固定渲染器只展示 `a[(j+k) mod capacity]` 映射与 FIFO
逻辑顺序。`ARRAY_QUEUE_ENQUEUE` v1 Schema 在相同循环数组参数外增加非空 `enqueueValue`，并要求
`elements.length < capacity`；固定渲染器只回放 `a[(j+n) mod capacity]` 的写入与 `n` 增加。
`ARRAY_QUEUE_DEQUEUE` v1 Schema 使用同一循环数组参数，但只要求非空 `elements`；固定渲染器只回放
读出 `a[j]`、`j = (j+1) mod capacity` 与 `n` 减一。`ARRAY_QUEUE_RESIZE` v1 Schema 使用
`previousCapacity`（2–12）、`headIndex` 和非空 `elements`，且逻辑元素必须跨越旧数组末端；固定渲染器
只回放 `b[k] = a[(j+k) mod oldCapacity]` 的 FIFO 复制与 `j = 0`，不解释触发条件或摊还复杂度。
`ARRAY_DEQUE_REPRESENTATION` v1 Schema 使用 `capacity`（2–12）、`headIndex`、非空 `elements` 与
范围内的 `accessIndex`；固定渲染器只回放逻辑下标 `i` 到 `a[(j+i) mod capacity]` 的映射和 `get(i)`
访问，不实现插入、删除、搬移或脚本化行为。
`ARRAY_DEQUE_FRONT_SHIFT_INSERT` v1 Schema 使用 `capacity`（3–12）、`headIndex`、至少两个且未满的
`elements`、靠近逻辑前端的 `insertIndex` 与非空 `insertValue`；固定渲染器只回放 `j` 左移回绕、前缀
搬移和写入，不实现尾端分支、删除、resize 或通用操作脚本。
`DUAL_ARRAY_DEQUE_REPRESENTATION` v1 Schema 使用长度至多 6 的 `front`、`back` 字符串数组和范围内的
`accessIndex`，且两栈合计至少一个元素；固定渲染器只回放 `reverse(front)` 后接 `back` 的逻辑顺序与
前缀反向/后缀偏移下标映射。`DUAL_ARRAY_DEQUE_BALANCE` v1 Schema 使用同样受限的两栈数组，要求总元素
数至少为 2 且一侧严格超过另一侧三倍；固定渲染器只回放按逻辑顺序重建为近似等大的两栈，不接受操作、
脚本或用户运行态。
`ROOTISH_ARRAY_STACK_LAYOUT` v1 Schema 使用 1–5 个数组块，且第 b 个块恰有 b+1 个非空字符串元素；
固定渲染器只回放递增块容量、逻辑顺序与总容量公式，不接受下标公式、脚本、动态组件或用户运行态。
`SEQUENTIAL_LIST_STORAGE` v1 Schema 使用受限的首地址、元素宽度、1–8 个非空逻辑元素及范围内访问下标；
固定渲染器只回放连续地址和 `LOC(ai)=LOC(a1)+(i-1)l` 的直接寻址，不接受地址表达式执行、脚本或用户运行态。
所有类型都拒绝把课件 JSON 解释为脚本、动态组件或通用可执行配置。

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
