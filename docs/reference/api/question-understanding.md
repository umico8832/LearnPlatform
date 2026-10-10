# 题目理解 API

M3 分级提示与辅助记录见[题目导师 API](question-tutor.md)。

本页说明已实现的预置单选题理解流程。响应仍使用[统一外壳](conventions.md#统一响应)；以下字段表只列 `data`。M2 不在请求时生成 AI 内容。数据结构与审核门禁见[数据库学习内容域](../database/learning-domain.md#题目理解)。

## 通用边界

- 全部接口要求登录。每次读取和提交都重新核对题目启用、未删除、`QuestionAccessPolicy` 的 PUBLIC/本人 PRIVATE 规则，以及所属课程的当前访问规则；本人历史记录不能绕过现时题目和课程访问。无权访问、题目或已审核内容不存在、内容被撤回时统一返回 `1004`，不暴露资源存在性。其他错误沿用[通用约定](conventions.md#错误语义)。
- M2 仅支持 `SINGLE_CHOICE` 且有且只有一个有效正确选项。`answer` 必须是该题现有、未删除的选项标签，大小写规范化为大写；`answerTime` 可省略，传入时为非负整数秒。无效输入返回 `1001`，无有效判分依据时返回 `1005`，均不写学习事实。
- 仅提供最新内容版本为 `REVIEWED` 且与当前题干、有效选项和答案指纹匹配的题目；最新版本被撤回时不回退到旧版。选项误区、正解和可视化须与同一版本一起审核。`viz.spec` 是 JSON 对象，由 `frontend/src/viz/` 的确定性模拟器解释；后端只存取，不计算轨迹。内容审核时核对 `scene`、`op`、参数及 `pauseAt` 中的真实 predict id；`pauseAt` 可包含 predict id 字符串或 `{ "id": "<predict id>", "guess": "<学习者在这一步的想法>" }`，空数组表示不停下提问，省略则在所有检查点提问。错误选项的 spec 使用这道题的数据。
- `source.verification` 只描述来源核对：`VERIFIED` 表示已与可追溯来源逐题核对题号、选项和答案，题干仅统一标点与本站问法时须在核对记录中说明；`REVIEWED` 表示 AI 生成内容经人工审核，`UNREVIEWED` 表示尚无上述来源核对。这些结论由内容审核写入，服务端只按存储的核对结论展示，不再自行核验来源。理解内容自身必须 `REVIEWED` 才公开；不能把 TutorContent 的知识点课件审核状态或题目定期复审状态当作本题理解内容审核。来源 URL 缺失时为 `null`，不得把投稿 ID 或导入批次伪装成 URL。
- 表内可选字段在无内容时为 `null`；客户端不能从 GET 题目或未提交的变式响应取得答案。原题 `attemptId` 是理解尝试 ID，不是练习记录 ID。

## 接口

| 方法与路径 | 请求 | 成功 `data` | 答案开放 |
|---|---|---|---|
| `GET /api/understanding/questions/{questionId}` | 无 | `questionId`、`stem`、`questionType`、`options`、`position`、`source` | 不含答案、正确标记和解析 |
| `POST /api/understanding/questions/{questionId}/attempts` | `answer`、可选 `answerTime` | `attemptId`、`correct`、`answer`、`misconception`、`viz` | 不含正确答案；只在选错且该选项有审核内容时给误区；选对时 `misconception` 和 `viz` 为 `null` |
| `POST /api/understanding/attempts/{attemptId}/solution/reveal` | 无请求体 | `correctAnswer`、`explanation`、`explanationSource`、`viz` | 只限该尝试的作答者；首次调用持久标记正解已开放，重复调用返回相同结果；M3 已审核提示链可用的错答须先领完三级提示 |
| `GET /api/understanding/attempts/{attemptId}/variant` | 无 | `variantId`、`stem`、`options`、`changedCondition`、`source` | 只限该尝试的作答者且已开放正解；不含答案；读取作答时锁定的已批准变式 |
| `POST /api/understanding/variants/{variantId}/attempts` | `answer`、可选 `answerTime`、`originAttemptId` | `variantAttemptId`、`correct`、`correctAnswer`、`explanation`、`explanationSource`、`viz`、`assistance` | 只限原尝试的作答者；首次提交后开放答案 |

`options` 元素为 `{ "label": "A", "content": "..." }`，按题目选项顺序返回，不含 `isCorrect`。`position` 为 `{courseId, courseName, chapterName, knowledgePointId, knowledgePointName}`：理解内容指定一个已关联且属于题目课程的主知识点，`chapterName` 取其所属顶层知识点名称；主知识点自身位于顶层时同名。位置缺失或失效时不发布理解内容，不从多个知识点中任意取第一个。

`source` 为 `{kind, title, url, verification}`，`title` 可为 `null`（例如变式的来源只标明 AI 生成）。`OFFICIAL_EXAM` 只能来自本题明确绑定的、已发布且 `sourceVerified=true` 的官方试卷题号，并留有本题逐题核验人和时间且核验结论为 `VERIFIED`；未完成逐题核对的试卷题暂按 `MANUAL/UNREVIEWED` 展示；标题优先取已审核的理解内容来源标题，缺失时由考试名、年份和展示题号组成，URL 仅取确认为 URL 的试卷来源；其他题按 `question.source_type` 区分 `AI_GENERATED` 与 `MANUAL`（投稿和导入归入后者）。AI 变式只在 `APPROVED` 后返回 `AI_GENERATED/REVIEWED`。不能仅凭题目 `source_reference` 或试卷类型声称 `VERIFIED`。

原题提交在同一事务中复用练习判分、练习记录、错题同步、复习计划和课程学习事件，另建一条与 `practice_record` 一对一的理解尝试；服务层可取得标准答案，但此响应投影绝不传给客户端。错项讲解只按本次选择的选项 ID 查找。若该选项没有审核讲解，`misconception` 和 `viz` 均为 `null`。若讲解存在但无可视化，只有 `viz=null`。`misconception` 为 `{title, explanation, source: {kind, reviewStatus}}`，其中 `kind` 为 `CURATED` 或 `AI_GENERATED`，`reviewStatus` 在公开响应中恒为 `REVIEWED`。

正解 `explanation` 取本次锁定的理解内容版本；`viz` 结构为 `{title, spec, source}`，无内容时为 `null`。正解以幂等 POST 显式开放并记录 `solution_revealed_time`，让变式必须在正解之后。版本仍有效时重复开放返回相同结果；被撤回时返回 `1004`。原题选对也可开放正解和变式。提示门禁的详细条件见[题目导师 API](question-tutor.md)。

变式复用 `ai_variant_question` 的题干、选项、私有答案、解析与 `APPROVED` 审查状态，通过其 `asset_id → question_ai_asset.question_id` 关联原题。M2 增加 `changed_condition`，审核时确认只改一个可核对条件并保留所测概念；可选 `viz` 同样只存 spec。创建原题尝试时锁定当前批准的变式 ID，后续内容变化不悄悄换题。变式取得与提交都重新检查其审核状态、母题关系和原题访问，未开放正解或无可用变式时返回 `1004`。一个原尝试只接受一条变式作答：并发或重试返回已锁定的首次结果，若再次请求的答案不同则返回 `1005`；不得形成第二次学习事件。`assistance` 由服务端写入，`UNAIDED` 表示本次原尝试未领取提示，`AIDED` 表示已领取提示；客户端不能传入。M4 只能以这一持久事实作为独立无提示评分候选，并自行决定 FSRS 映射。

当前预置题目由迁移中的题号、课程内容键与知识点内容键定位；数据库自增 ID 不作为固定契约。
