# 社区共建 API

社区以登录账号共享话题与学习资料，作者身份来自服务端认证；不接受客户端传入作者名称或用户 ID。
学习端入口为 `/community`，管理端为 `/admin/community`。原有题目评论与单题投稿接口保持兼容。

## 分类与内容

| 接口 | 说明 |
|---|---|
| `GET /api/community/categories` | 考试、科目与学校分区目录 |
| `GET /api/community/posts` | 分页查询可见社区内容 |
| `POST /api/community/posts` | multipart 创建话题或资料投稿 |
| `GET /api/community/posts/{id}` | 正文、来源、附件元数据与题目整理进度 |
| `DELETE /api/community/posts/{id}` | 作者或管理员删除内容及附件 |
| `GET /api/community/posts/{id}/reviews` | 作者或管理员查看审核记录 |
| `GET /api/community/attachments/{id}` | 按内容权限下载附件原始字节 |

分类目录继承 AiStu 的考试与科目标识；学校分区由管理员创建，不初始化示例学校或示例帖子。
考试是科目的父级，创建时提交 `subjectId`，服务端据目录推导考试，避免两者不一致。
`schoolId` 可选，当前学校分区开放全部科目。可附加平台课程 `courseId` 与该课程内的
`knowledgePointId`；课程必须存在、开放且未删除。`conceptName` 是独立的讨论主题文本。

列表支持 `keyword`、`contentType`、`examId`、`subjectId`、`schoolId`、`courseId`、`mine` 和 `status`。
关键词按字面搜索标题、正文和主题；默认按创建 ID 倒序。`pageNum` 为 1–100000，`pageSize` 为 1–50，
默认每页 20 条；响应为 `records`、`total`、`current`、`size`。

`contentType` 支持 `TOPIC`、`explanation`、`analogy`、`common_mistake`、`exam_method`、`syllabus`、
`question_bank`。话题直接 `APPROVED`，其余投稿初始为 `PENDING`，并必须填写来源与使用许可。
普通列表只返回公开内容及本人的未公开内容；管理员全量审核列表使用管理接口。
关联课程不可用时内容不再向其他普通用户公开。详情、评论和附件下载遵守相同可见性规则。
正文在前端按纯文本展示，不执行提交者提供的 HTML。

### 附件

创建接口使用 JSON 类型的 `post` 部件与多个 `files` 文件部件。仅 `question_bank` 可以附加文件，
且必须包含 1–8 个非空附件；每个最大 30 MiB，每个用户社区附件总量最大 240 MiB。
允许 PDF、DOC、DOCX、WPS、XLS、XLSX、ET、CSV、JSON、TXT、MD、ZIP 扩展名，拒绝路径或控制字符文件名。
当前保存、审核和下载原文件，不执行、解压或自动解析这些附件；扩展名校验不代表文件内容质量审核。
待审、驳回或隐藏内容及其附件仅作者和管理员可读取。
下载强制使用 `application/octet-stream`、附件处置与 `nosniff`，并禁止共享缓存。

## 讨论互动

| 接口 | 说明 |
|---|---|
| `GET /api/community/posts/{id}/comments` | 分页查询回复，按创建顺序排列 |
| `POST /api/community/posts/{id}/comments` | 发布回复，可指定同话题内的 `parentId` |
| `DELETE /api/community/comments/{id}` | 作者或管理员删除回复正文 |
| `PUT /api/community/posts/{id}/like` | 幂等赞同内容 |
| `DELETE /api/community/posts/{id}/like` | 幂等取消赞同 |
| `PUT /api/community/posts/{id}/comments/{commentId}/like` | 幂等赞同同话题内的回复 |
| `DELETE /api/community/posts/{id}/comments/{commentId}/like` | 幂等取消回复赞同 |

仅公开内容允许新增回复或点赞。回复长度最多 2000 字，拒绝跨话题或已删除的回复目标。
删除回复保留占位以维持回复关系，但清空正文；列表总数包含占位，内容卡片回复数只统计未删除回复。
客户端不提交计数，服务端从关系表统计；唯一键与话题行锁共同保护并发幂等。

## 管理审核与题目整理

| 接口 | 说明 |
|---|---|
| `GET /api/admin/community/posts` | 管理员按关键词与状态分页查询全部未删除内容 |
| `POST /api/admin/community/posts/{id}/review` | 公开、驳回或隐藏内容，追加审核记录 |
| `POST /api/admin/community/schools` | 创建名称唯一的学校分区 |
| `POST /api/admin/community/posts/{id}/questions` | 从已公开题库资料整理为既有单题投稿 |

管理入口执行 Spring Security 角色检查，业务服务再次核对数据库中的当前角色和账号状态。
审核请求包含 `decision`（`APPROVED`、`REJECTED`、`HIDDEN`）与必填 `note`；重复设置同一状态拒绝，
改变状态会保留审核人、意见与时间。驳回或隐藏后停止其他用户的访问，可以重新审核公开。

整理题目使用既有 `QuestionSubmissionRequest`，另需 16–64 位字母、数字或连字符组成的
`Idempotency-Key` 请求头。同一社区内容与请求标识重复提交返回原投稿，不重复创建。
有关联课程时题目课程必须一致；知识点必须属于该课程。作者保留为原资料贡献者，来源固定为
`community:{id}`，原社区记录继续保存完整来源说明。新题目初始待审，仍需通过现有
[单题投稿审核与显式入库](admin-governance.md)流程；社区审核通过本身不会生成题目或写入知识库。
