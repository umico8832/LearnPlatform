# 考试 API

## 用户端考试

| 接口 | 说明 |
|---|---|
| `GET /api/exam/papers` | 查询已发布试卷 |
| `GET /api/exam/papers/{id}` | 获取可作答试卷详情 |
| `POST /api/exam/start/{paperId}` | 创建或恢复考试记录 |
| `POST /api/exam/submit` | 提交考试答案 |
| `GET /api/exam/result/{recordId}` | 获取本人考试结果 |
| `GET /api/exam/records` | 查询本人考试记录 |

## 用户端试卷学习

| 接口 | 说明 |
|---|---|
| `POST /api/exam/papers/{paperId}/learning-sessions` | 创建或恢复当前用户在该试卷上的进行中学习会话 |
| `GET /api/exam/learning-sessions/{sessionId}` | 获取本人学习会话、原始题号结构和每题最近一次作答 |
| `POST /api/exam/learning-sessions/{sessionId}/answers` | 逐题提交答案并立即返回服务端判分与解析 |
| `POST /api/exam/learning-sessions/{sessionId}/complete` | 在全部题目至少作答一次后完成本轮学习 |

试卷学习模式仅面向已发布、已关联课程且课程已加入当前用户课程库的试卷。同一用户和试卷最多保留一个
进行中会话；完成后可以开始新一轮。每道题允许多次尝试，服务端保存尝试序号，并以最近一次结果生成
会话摘要。未作答题目不返回正确选项、正确答案和解析；逐题提交后只返回该题的判分与解析，完成态页面
可继续作为只读逐题复盘入口。学习模式不创建或复用 `exam_record`，也不改变考试模式的时限与一次提交
语义。

## 判分边界

- 开始考试后，后端以试卷题目配置和考试记录锁定本次考试。
- 提交时校验试卷归属、题号重复、题目集合和考试时限。
- 分数由后端计算，客户端提交的展示分数不可信。
- 试卷详情在提交前不得暴露正确选项和题目解析。
- 试卷学习逐题答案同样由后端计算；客户端只提交题目、答案和可选耗时，不能指定正确性或得分。
- 学习会话始终绑定创建者，其他用户即使加入同一课程也不能读取、作答或完成该会话。

已发布试卷的列表与详情还会返回以下来源字段：

| 字段 | 说明 |
|---|---|
| `paperType` | 当前可写值为 `PRACTICE` 或 `OFFICIAL_EXAM` |
| `examName` / `examYear` | 原始考试名称与年份；普通练习可为空 |
| `sourceReference` / `sourceVerified` | 可复核来源及管理员核验结果 |
| `questions[].sectionTitle` | 原试卷分区或大题标题 |
| `questions[].majorQuestionNumber` / `minorQuestionNumber` / `subquestionNumber` | 分层题号 |
| `questions[].displayNumber` | 学习端直接展示的完整原始题号 |

## 管理端试卷

| 接口 | 说明 |
|---|---|
| `GET /api/admin/exam-papers` | 查询试卷 |
| `GET /api/admin/exam-papers/{id}` | 获取管理端试卷详情 |
| `POST /api/admin/exam-papers` | 创建试卷 |
| `PUT /api/admin/exam-papers/{id}` | 修改未受锁定限制的试卷 |
| `DELETE /api/admin/exam-papers/{id}` | 删除允许删除的试卷 |
| `POST /api/admin/exam-papers/{id}/publish` | 发布试卷 |
| `POST /api/admin/exam-papers/smart-preview` | 预览智能组卷结果 |
| `POST /api/admin/exam-papers/smart-create` | 根据确认条件创建智能试卷 |

已发布试卷以及被考试记录引用的配置受不可变规则保护。

创建和更新接口接受上述来源与题号字段。`OFFICIAL_EXAM` 可以先保存为草稿，但通过创建、更新或
独立发布接口进入已发布状态时，服务端必须确认有效考试名称与年份、非空来源、`sourceVerified=true`
以及每道题的 `displayNumber`；任一条件缺失均拒绝发布。智能组卷默认仍属于 `PRACTICE`，不能自动
获得官方原题标记。用户私有试卷尚未建立所有者隔离和可见性规则，因此当前接口不接受对应类型。
