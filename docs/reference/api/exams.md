# 考试 API

## 用户端考试

| 接口 | 说明 |
|---|---|
| `GET /api/exam/papers` | 查询已发布试卷 |
| `GET /api/exam/papers/{id}` | 获取可作答试卷详情 |
| `POST /api/exam/start/{paperId}` | 创建或恢复考试记录 |
| `GET /api/exam/records/{recordId}/session` | 获取本人限时考试会话、服务端时钟和试卷引用；不返回答案 |
| `POST /api/exam/submit` | 提交考试答案 |
| `GET /api/exam/result/{recordId}` | 获取本人考试结果 |
| `GET /api/exam/records` | 查询本人考试记录 |
| `POST /api/exam/private-papers/import/preview` | 解析有限结构化 Markdown/文本，只返回预览且不写库 |
| `POST /api/exam/private-papers/import/confirm` | 携带预览内容哈希并显式确认后创建本人私有试卷 |
| `POST /api/exam/private-papers/drafts` | 将含缺失答案的预览保存为本人复核草稿 |
| `POST /api/exam/private-papers/import/pdf/preview` | 从文本型 PDF 提取已有文本并进入同一无写库预览 |
| `POST /api/exam/private-papers/import/pdf/confirm` | 重新上传同一 PDF，以原文件哈希确认并创建本人私有试卷 |
| `POST /api/exam/private-papers/drafts/pdf` | 重新上传同一 PDF，将含缺失答案的预览保存为本人复核草稿 |
| `POST /api/exam/private-papers/import/docx/preview` | 从有限 DOCX 提取普通段落和表格并进入同一无写库预览 |
| `POST /api/exam/private-papers/import/docx/confirm` | 重新上传同一 DOCX，以原文件哈希确认并创建本人私有试卷 |
| `POST /api/exam/private-papers/drafts/docx` | 重新上传同一 DOCX，将含缺失答案的预览保存为本人复核草稿 |
| `GET /api/exam/private-papers/drafts` | 查询本人尚未确认启用的私有试卷草稿 |
| `GET /api/exam/private-papers/drafts/{draftId}` | 读取本人草稿和逐题复核状态 |
| `POST /api/exam/private-papers/drafts/{draftId}/questions/{questionId}/ai-answer` | 为一题生成受结构校验的 AI 答案与解析建议 |
| `PUT /api/exam/private-papers/drafts/{draftId}/questions/{questionId}/review` | 所有者提交该题最终答案和解析并标记已复核 |
| `POST /api/exam/private-papers/drafts/{draftId}/confirm` | 全部题目复核后显式确认并启用私有试卷 |
| `DELETE /api/exam/private-papers/drafts/{draftId}` | 所有者删除尚未确认启用的私有试卷草稿 |
| `DELETE /api/exam/private-papers/{paperId}` | 所有者删除尚未产生考试、学习或衍生事实的私有试卷 |
| `GET /api/exam/private-papers/{paperId}/source` | 仅所有者读取该私有试卷的原始资料 |
| `GET /api/exam/private-papers/{paperId}/source/file` | 仅所有者下载已确认试卷的 PDF/DOCX 原文件 |
| `GET /api/exam/private-papers/drafts/{draftId}/source/file` | 仅所有者下载待复核草稿的 PDF/DOCX 原文件 |
| `GET /api/exam/private-papers/source-storage` | 查询本人原文件累计用量、配额、剩余空间和文件数 |

同一用户对同一张已发布试卷最多保留一个活动考试记录。再次调用开始接口时，未过期记录会被复用；
已过期记录会固化为超时并释放活动键，再创建新一轮。考试状态为 `0` 进行中、`1` 已完成、`2` 已超时、
`3` 待人工批阅。含主观题的考试提交后先以自动判分题所得分数作为暂定分；全部主观题完成批阅后才进入
已完成状态并固化最终总分。

会话接口只允许记录所属用户读取，返回带 UTC 偏移的 `deadline` 和 `serverTime`，供客户端在刷新或计时器
被浏览器节流后恢复同一记录、试卷和服务端剩余时限；响应不包含逐题答案。刷新前尚未提交的客户端作答
不会恢复，考试时限最终仍由提交接口在服务端校验。

## 用户私有试卷导入

首期接受不超过 100000 字符、最多 100 题的结构化 `MARKDOWN`、`TEXT`，最大 10MB、200 页且已有
文本层的 `PDF`，以及最大 10MB 且只含普通段落/表格文本的 `DOCX`，并仅支持单选、多选和判断题。
文件接口使用 `multipart/form-data`，其中 `metadata` 为
JSON part、`file` 为上传文件 part；服务端校验扩展名、文件头、大小和提取文本长度，PDF 额外校验页数。空文本、扫描件、
加密或损坏 PDF 会明确拒绝，不执行 OCR、复杂版式猜测或外部内容识别。
DOCX 会按正文顺序提取普通段落和表格单元格，支持中英文结构化字段；图片、公式、文本框、嵌套表格、
页眉页脚及其他复杂对象会明确拒绝，不尝试静默丢弃后继续导入。

预览接口校验课程、题干、选项和分值，但不产生数据库写入；答案完整时，确认接口必须提交同一原始内容的
SHA-256 哈希及 `confirmed=true`，内容变化后必须重新预览。PDF 的哈希以原始上传字节计算，确认或创建
草稿时必须重新上传同一文件，服务端会再次校验、提取并比对哈希；DOCX 使用相同的原始字节哈希约束。
确认成功后试卷和拆解题目均标记为
`PRIVATE`、绑定 `ownerUserId`，原始名称、格式、哈希和全文保存到仅所有者可读取的资料记录。
确认或创建草稿时，PDF/DOCX 资料记录还保存最大 10MB 的原始字节、规范媒体类型和长度。下载接口只接受
当前所有者的私有试卷或草稿引用，返回附件响应并使用 `private, no-store` 与 `nosniff`；发送前重新校验
长度、媒体类型和 SHA-256。接口不生成公共 URL，不向管理员开放正文或原文件。Markdown/文本来源只保留
既有正文追溯，不提供不存在的原文件下载。

原文件累计配额由 `PRIVATE_EXAM_SOURCE_STORAGE_LIMIT_BYTES` 配置，本地默认 100MB。确认或创建草稿的事务
先锁定所有者用户行，再汇总现存 `source_size` 并判断本次原文件是否可写入，避免并发请求同时越过上限；
超过配额返回额度错误且不创建来源、草稿或试卷。来源随既有生命周期删除后，用量由当前记录实时汇总并
自然释放。用量查询不读取 BLOB。

存在缺失答案时只能创建复核草稿，不能直接确认导入。每道缺失答案题可单独调用 AI 建议接口；模型输出
必须是严格 JSON，答案标签必须属于现有选项并满足单选/判断一个、多选至少两个的结构规则。AI 建议不会
直接写入正式题目的正确选项，所有者必须逐题提交最终答案和解析；全部题目进入 `REVIEWED` 后草稿才进入
`READY`。确认启用在事务中锁定草稿，创建一次正式私有试卷并记录关联试卷 ID，重复确认返回同一试卷。
未确认草稿不会出现在试卷列表、考试、学习、公共题库或管理端。AI 调用继续执行个人配额和调用审计。

私有试卷会出现在所有者的可用试卷列表中，可进入考试模式；关联课程已加入课程库时也可进入学习模式。
其他用户和管理端的试卷、题库、搜索、练习候选、AI 资产及统计接口不会返回私有正文。该入口不调用
管理员官方试卷写接口，不自动公开，也不把用户资料标记为官方原题。

未确认草稿可由所有者直接删除；`CONFIRMED` 草稿只能通过其已启用私有试卷统一清理。私有试卷只有在
没有考试记录、试卷学习会话、逐题作答、错题、收藏、复习计划或其他题目衍生内容时才能删除。删除操作
在事务中锁定并物理清理专属试卷、拆解题目、选项和关联草稿；原始资料仅在不再被任何草稿或试卷引用时
删除，原文件二进制随同无人引用的来源记录一起清理。跨账号读取、下载或删除均按不存在处理，避免暴露
私有内容标识。

## 用户端试卷学习

| 接口 | 说明 |
|---|---|
| `POST /api/exam/papers/{paperId}/learning-sessions` | 创建或恢复当前用户在该试卷上的进行中学习会话 |
| `GET /api/exam/learning-sessions/{sessionId}` | 获取本人学习会话、原始题号结构和每题最近一次作答 |
| `POST /api/exam/learning-sessions/{sessionId}/answers` | 逐题提交答案并立即返回服务端判分与解析 |
| `POST /api/exam/learning-sessions/{sessionId}/questions/{questionId}/ai/{assistanceType}/stream` | 基于本人当前学习会话和该题最近一次作答流式生成解析或变式辅导 |
| `POST /api/exam/learning-sessions/{sessionId}/complete` | 在全部题目至少作答一次后完成本轮学习 |

试卷学习模式仅面向已发布、已关联课程且课程已加入当前用户课程库的试卷。同一用户和试卷最多保留一个
进行中会话；完成后可以开始新一轮。每道题允许多次尝试，服务端保存尝试序号，并以最近一次结果生成
会话摘要。未作答题目不返回正确选项、正确答案和解析；逐题提交后只返回该题的判分与解析，完成态页面
可继续作为只读逐题复盘入口。学习模式不创建或复用 `exam_record`，也不改变考试模式的时限与一次提交
语义。`SHORT_ANSWER` 在学习模式中保存答案并返回 `SELF_REVIEW`，允许作答后查看参考解析，但不使用
关键词命中伪造正确性、得分、错题或课程判分事件。

AI 辅导只接受 `explanation` 和 `variant` 两种类型，并要求当前题已经至少作答一次。服务端验证会话创建者、
课程成员关系、试卷与题目归属，再将最近一次答案、尝试序号和判分结果写入本次调用上下文；成功调用会形成
课程学习事件。交互记录只保存这些业务引用、状态和失败摘要，不保存完整提示词或模型输出。通用题目助手
仍可用于其他练习入口，但不能替代这条试卷学习上下文链路。

## 判分边界

- 开始考试后，后端以试卷题目配置和考试记录锁定本次考试。
- 提交时校验试卷归属、题号重复、题目集合和考试时限。
- 分数由后端计算，客户端提交的展示分数不可信。
- 试卷详情在提交前不得暴露正确选项和题目解析。
- 已完成和待人工批阅记录可以读取本人结果；待批阅的主观题只展示答案与状态，不返回参考答案或解析。
  进行中或已超时记录不得通过结果接口读取答案和解析。
- 试卷学习逐题答案同样由后端计算；客户端只提交题目、答案和可选耗时，不能指定正确性或得分。
- 学习会话始终绑定创建者，其他用户即使加入同一课程也不能读取、作答或完成该会话。

试卷列表、详情、考试记录、会话和结果会按场景返回以下来源字段；已完成结果的 `answers[]` 还会返回
原始题号结构：

| 字段 | 说明 |
|---|---|
| `paperType` | 公共管理接口为 `PRACTICE` 或 `OFFICIAL_EXAM`；确认的用户资料为 `USER_PRIVATE` |
| `visibility` / `ownerUserId` | `PUBLIC` 公共内容，或仅所有者可见的 `PRIVATE` 内容 |
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
| `GET /api/admin/exam-papers/subjective-reviews/pending` | 查询待人工批阅的主观题答案及评分点 |
| `POST /api/admin/exam-papers/subjective-reviews/{answerId}` | 逐评分点提交得分与评语并重算考试总分 |

已发布试卷以及被考试记录引用的配置受不可变规则保护。

创建和更新接口接受上述来源与题号字段。`OFFICIAL_EXAM` 可以先保存为草稿，但通过创建、更新或
独立发布接口进入已发布状态时，服务端必须确认有效考试名称与年份、非空来源、`sourceVerified=true`
以及每道题的 `displayNumber`；任一条件缺失均拒绝发布。智能组卷默认仍属于 `PRACTICE`，不能自动
获得官方原题标记。用户私有试卷只能由上述用户端预览—确认接口创建，管理接口不接受 `USER_PRIVATE`，
也不会列出或读取私有试卷正文。

包含 `SHORT_ANSWER` 的试卷发布前还必须为每道主观题配置非空评分点，且评分点满分之和严格等于该题在
试卷中的分值。人工批阅请求必须一次提交全部评分点，评分点不能缺失、重复或越界；服务端锁定答案与考试
记录后保存评分明细，只有同一记录下全部待批阅答案完成时才将考试状态从 `3` 更新为 `1`。
