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

## 判分边界

- 开始考试后，后端以试卷题目配置和考试记录锁定本次考试。
- 提交时校验试卷归属、题号重复、题目集合和考试时限。
- 分数由后端计算，客户端提交的展示分数不可信。
- 试卷详情在提交前不得暴露正确选项和题目解析。

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
