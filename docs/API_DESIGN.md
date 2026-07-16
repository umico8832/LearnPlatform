# AI 题库与错题复习系统 - 接口设计文档

> 用户端题目与已发布试卷详情不会返回选项正确标记或题目解析。考试提交由后端锁定考试记录，校验题目归属、重复题号和考试时限，并以试卷题目配置计算总分。已发布试卷及其引用题目不可修改或删除。

## 一、接口规范

### 1.1 基础信息

| 项目 | 说明 |
|------|------|
| 基础路径 | `/api` |
| 管理端路径 | `/api/admin` |
| 认证方式 | Bearer Token（JWT） |
| 内容类型 | `application/json` |
| 接口文档 | `http://localhost:8080/doc.html`（Knife4j） |

### 1.2 请求头

| Header | 必填 | 说明 |
|--------|:----:|------|
| Authorization | 是（除公开接口） | `Bearer <token>` |
| Content-Type | 是 | `application/json` |

### 1.3 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 1.4 响应码定义

| code | 说明 |
|------|------|
| 0 | 成功 |
| 1001 | 参数校验失败 |
| 1002 | 未登录 / Token 无效 |
| 1003 | 无权限 |
| 1004 | 资源不存在 |
| 1005 | 业务异常（如用户名已存在） |
| 5000 | 系统异常 |

### 1.5 分页请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| page | int | 否 | 1 | 页码 |
| pageSize | int | 否 | 10 | 每页数量 |

### 1.6 分页响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

---

## 二、公开接口（无需认证）

### 2.1 用户注册

```
POST /api/auth/register
```

**请求体**：
```json
{
  "username": "string",    // 用户名，3-20字符，唯一
  "password": "string",    // 密码，6-20字符
  "nickname": "string"     // 昵称，可选，2-20字符
}
```

**响应**：
```json
{
  "code": 0,
  "message": "注册成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 604800,
    "user": {
      "id": 1,
      "username": "testuser",
      "nickname": "测试用户",
      "avatar": null,
      "role": "USER"
    }
  }
}
```

### 2.2 用户登录

```
POST /api/auth/login
```

**请求体**：
```json
{
  "username": "string",    // 用户名
  "password": "string"     // 密码
}
```

**响应**：同注册响应格式。

### 2.3 健康检查

```
GET /api/public/health
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "timestamp": 1700000000000
  }
}
```

---

## 三、用户接口（需认证）

### 3.1 获取当前用户信息

```
GET /api/users/me
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "username": "testuser",
    "nickname": "测试用户",
    "avatar": null,
    "role": "USER",
    "createTime": "2024-01-01 00:00:00"
  }
}
```

### 3.2 修改个人信息

```
PUT /api/users/profile
```

**请求体**：
```json
{
  "nickname": "string",    // 昵称，可选
  "avatar": "string"       // 头像URL，可选
}
```

### 3.3 修改密码

```
PUT /api/users/password
```

**请求体**：
```json
{
  "oldPassword": "string", // 旧密码
  "newPassword": "string"  // 新密码，6-20字符
}
```

---

## 四、课程接口

### 4.1 用户端 - 课程列表

```
GET /api/courses
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "Java 基础",
      "description": "Java 编程语言基础知识",
      "coverImage": null,
      "sortOrder": 1
    }
  ]
}
```

### 4.2 用户端 - 课程详情（含知识点）

```
GET /api/courses/{id}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "name": "Java 基础",
    "description": "...",
    "knowledgePoints": [
      {
        "id": 1,
        "name": "Java 语法",
        "parentId": 0,
        "children": [
          {
            "id": 2,
            "name": "变量与数据类型",
            "parentId": 1,
            "children": []
          }
        ]
      }
    ]
  }
}
```

---

## 五、知识点接口

### 5.1 用户端 - 按课程查询知识点树

```
GET /api/knowledge-points/tree?courseId={courseId}
```

---

## 六、题库接口

### 6.1 用户端 - 查询题目列表（筛选）

```
GET /api/questions
```

**查询参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| courseId | Long | 否 | 课程ID |
| knowledgePointId | Long | 否 | 知识点ID |
| questionType | String | 否 | 题型 |
| difficulty | Integer | 否 | 难度 1-5 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页数量 |

### 6.2 用户端 - 题目详情

```
GET /api/questions/{id}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "content": "以下哪个是 Java 的基本数据类型？",
    "questionType": "SINGLE_CHOICE",
    "difficulty": 2,
    "analysis": "Java 的基本数据类型包括...",
    "tags": "Java基础,数据类型",
    "courseId": 1,
    "courseName": "Java 基础",
    "knowledgePoints": [
      { "id": 1, "name": "变量与数据类型" }
    ],
    "options": [
      { "id": 1, "optionLabel": "A", "content": "String" },
      { "id": 2, "optionLabel": "B", "content": "int" },
      { "id": 3, "optionLabel": "C", "content": "ArrayList" },
      { "id": 4, "optionLabel": "D", "content": "HashMap" }
    ]
  }
}
```

> **注意**：用户端获取题目时不返回 `isCorrect` 字段，提交答案后才返回正确选项。

### 6.3 用户端 - 提交题目纠错反馈

```
POST /api/questions/{id}/correction-reports
GET  /api/questions/correction-reports/my
```

**提交请求体**：
```json
{
  "reportType": "ANSWER",
  "description": "我认为本题正确答案应为 B，当前选项和解析不一致。"
}
```

说明：`reportType` 取值为 `CONTENT`、`ANSWER`、`ANALYSIS`、`KNOWLEDGE_POINT`、`OTHER`。提交后状态为 `OPEN`，不会自动修改题目内容、答案或解析。

---

## 七、刷题接口

### 7.1 获取练习题目

```
GET /api/practice/questions
```

**查询参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| mode | String | 是 | 练习模式：COURSE / KNOWLEDGE_POINT / RANDOM / SEQUENCE |
| courseId | Long | 条件 | 课程ID（COURSE 模式必填） |
| knowledgePointId | Long | 条件 | 知识点ID（KNOWLEDGE_POINT 模式必填） |
| count | Integer | 否 | 题目数量，默认 10 |

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "content": "...",
      "questionType": "SINGLE_CHOICE",
      "difficulty": 2,
      "options": [
        { "id": 1, "optionLabel": "A", "content": "选项A" },
        { "id": 2, "optionLabel": "B", "content": "选项B" }
      ]
    }
  ]
}
```

### 7.2 提交答案

```
POST /api/practice/submit
```

**请求体**：
```json
{
  "questionId": 1,
  "userAnswer": "B",          // 单选: "B", 多选: "A,C", 判断: "true"/"false"
  "answerTime": 30            // 答题耗时（秒），可选
}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "correct": false,
    "userAnswer": "B",
    "correctAnswer": "A",
    "analysis": "详细解析...",
    "explanation": null
  }
}
```

### 7.3 刷题记录

```
GET /api/practice/records
```

**查询参数**：分页参数

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "questionId": 1,
        "questionContent": "题目内容...",
        "userAnswer": "B",
        "correctAnswer": "A",
        "isCorrect": false,
        "answerTime": 30,
        "createTime": "2024-01-01 12:00:00"
      }
    ],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

### 7.4 收藏题练习

```
GET /api/practice/favorites
```

**说明**：从当前登录用户的收藏题目中获取练习题，返回结构与普通练习题一致，练习模式下不暴露正确答案和解析。

**查询参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| count | Integer | 否 | 题目数量，默认 10，最大 50 |
| questionId | Long | 否 | 指定收藏题目 ID；传入时只返回该题，并校验该题属于当前用户收藏 |

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "content": "题目内容...",
      "questionType": "SINGLE_CHOICE",
      "courseName": "Java 基础",
      "difficulty": 2,
      "score": 5,
      "options": [
        { "id": 1, "optionLabel": "A", "content": "选项A", "isCorrect": 0 },
        { "id": 2, "optionLabel": "B", "content": "选项B", "isCorrect": 0 }
      ],
      "knowledgePointIds": [1],
      "knowledgePointNames": ["基础语法"]
    }
  ]
}
```

---

## 八、错题本接口

### 8.1 错题列表

```
GET /api/wrong-questions
```

**查询参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| courseId | Long | 否 | 课程筛选 |
| knowledgePointId | Long | 否 | 知识点筛选 |
| masteryLevel | Integer | 否 | 掌握程度 0/1/2 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页数量 |

### 8.2 移出错题本

```
DELETE /api/wrong-questions/{id}
```

### 8.3 更新掌握状态

```
PUT /api/wrong-questions/{id}/mastery
```

**请求体**：
```json
{
  "masteryLevel": 1    // 0-未掌握 1-部分掌握 2-已掌握
}
```

### 8.4 错题重练

```
GET /api/wrong-questions/repractice
```

**查询参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| count | Integer | 否 | 题目数量，默认 10 |

### 8.5 高频错题知识点统计

```
GET /api/wrong-questions/statistics
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "knowledgePointId": 1, "knowledgePointName": "变量与数据类型", "wrongCount": 15 },
    { "knowledgePointId": 3, "knowledgePointName": "面向对象", "wrongCount": 10 }
  ]
}
```

---

## 九、考试接口

### 9.1 试卷列表（用户端）

```
GET /api/exam/papers
```

### 9.2 开始考试

```
POST /api/exam/start/{paperId}
```

`paperId` 通过路径参数传递。

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "recordId": 1,
    "paperId": 1,
    "title": "Java基础模拟考试",
    "duration": 60,
    "totalScore": 100,
    "startTime": "2024-01-01 12:00:00",
    "questions": [
      {
        "sortOrder": 1,
        "score": 5,
        "question": {
          "id": 1,
          "content": "...",
          "questionType": "SINGLE_CHOICE",
          "options": [...]
        }
      }
    ]
  }
}
```

### 9.3 提交考试

```
POST /api/exam/submit
```

**请求体**：
```json
{
  "examRecordId": 1,
  "answers": [
    { "questionId": 1, "userAnswer": "A" },
    { "questionId": 2, "userAnswer": "B,C" }
  ]
}
```

### 9.4 考试结果

```
GET /api/exam/result/{recordId}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "recordId": 1,
    "paperTitle": "Java基础模拟考试",
    "score": 80,
    "totalScore": 100,
    "correctCount": 16,
    "totalCount": 20,
    "startTime": "2024-01-01 12:00:00",
    "endTime": "2024-01-01 13:00:00",
    "answers": [
      {
        "questionId": 1,
        "content": "...",
        "userAnswer": "A",
        "correctAnswer": "A",
        "isCorrect": true,
        "score": 5,
        "analysis": "..."
      }
    ]
  }
}
```

### 9.5 考试记录列表

```
GET /api/exam/records
```

---

## 十、AI 接口

### 10.1 AI 生成题目解析

```
POST /api/ai/explanation
```

**请求体**：
```json
{
  "questionId": 1
}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": "## 解析\n\n这道题考查的是...",  // Markdown 格式
    "source": "ai"
  }
}
```

### 10.2 AI 生成变式题

```
POST /api/ai/variant
```

**请求体**：
```json
{
  "questionId": 1
}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": "## 变式题\n\n...",
    "source": "ai"
  }
}
```

### 10.2.1 AI 题目流式生成

```
POST /api/ai/explanation/stream
POST /api/ai/variant/stream
POST /api/ai/review-suggestion/stream
Accept: text/event-stream
Authorization: Bearer <token>
```

请求体与对应同步接口一致。服务端通过 SSE 返回以下事件：

```text
event: content
data: {"content":"生成内容分片"}

event: done
data: {"source":"ai"}
```

调用失败时返回 `error` 事件，数据格式为 `{"message":"错误信息"}`。同步接口继续保留用于兼容非流式调用场景。复习建议流式接口请求体同 `POST /api/ai/review-suggestion`，`courseId` 可选。

### 10.3 AI 复习建议

```
POST /api/ai/review-suggestion
```

**请求体**：
```json
{
  "courseId": 1    // 可选，针对特定课程
}
```

### 10.4 AI 知识点总结

```
POST /api/ai/summary
```

**请求体**：
```json
{
  "knowledgePointId": 1
}
```

### 10.5 AI 题目学习资产

将一道题从"题干 + 答案 + 解析"升级为结构化 AI 学习对象，支持 7 种资产类型：标准解析、小白版解析、步骤拆解、错误选项分析、常见误区、变式题、可视化讲解。

#### 10.5.1 查询已缓存资产

```
GET /api/ai/assets/{questionId}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "questionId": 42,
      "assetType": "FULL_EXPLANATION",
      "assetTypeLabel": "标准解析",
      "content": "## 📌 考查知识点\n...",
      "model": "gpt-4o-mini",
      "createTime": "2026-06-16 22:00:00"
    }
  ]
}
```

#### 10.5.2 同步生成/获取资产

```
POST /api/ai/asset/generate
```

**请求体**：
```json
{
  "questionId": 42,
  "assetType": "FULL_EXPLANATION"
}
```

**assetType 枚举值**：
| 值 | 说明 |
|---|------|
| FULL_EXPLANATION | 标准结构化解析 |
| BEGINNER_EXPLANATION | 小白版解析 |
| STEP_BY_STEP | 步骤拆解 |
| WRONG_OPTION_ANALYSIS | 错误选项分析 |
| COMMON_MISTAKES | 常见误区 |
| VARIANT | 变式题 |
| VISUAL_INTERACTIVE | 可视化交互讲解 |

有缓存直接返回，无缓存调用 AI 生成并缓存后返回。受每日 AI 调用配额限制。

当 `assetType=VARIANT` 且为 V19 后新生成的资产时，响应额外包含 `variantQuestion`：`id`、`questionType`、`questionContent`、`options` 和 `difficulty`。该对象刻意不返回 `correctAnswer` 与 `analysis`；标准答案只在提交后随首次判分结果返回。V19 前已有的 Markdown 变式缓存保持 `variantQuestion=null`，继续走旧版展示。

#### 10.5.3 流式生成资产

```
POST /api/ai/asset/stream
Accept: text/event-stream
Authorization: Bearer <token>
```

**请求体**：同 10.5.2。SSE 事件格式与 10.2.1 一致（`content` / `done` / `error` 事件）。生成完成后自动缓存。结构化变式题包含服务端私有答案，因此 `VARIANT` 会在完整 JSON 校验、私有答案落库和公开内容脱敏后一次性返回，不透传包含答案的上游流式片段。

#### 10.5.4 清除题目资产缓存

```
DELETE /api/ai/assets/{questionId}
```

删除指定题目的所有 AI 学习资产缓存。适用于题目内容更新后需要重新生成资产的场景。

#### 10.5.5 提交资产反馈

```
POST /api/ai/asset/feedback
```

**请求体**：
```json
{
  "questionId": 42,
  "assetType": "FULL_EXPLANATION",
  "helpful": true,
  "comment": "解释得很清楚"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| questionId | Long | 是 | 题目ID |
| assetType | String | 是 | 资产类型（见 10.5.2 枚举值表） |
| helpful | Boolean | 是 | true-有帮助 false-无帮助 |
| comment | String | 否 | 用户补充说明 |

同一用户对同一题同一资产类型只能反馈一次，重复提交会更新已有反馈。

#### 10.5.6 查询资产反馈

```
GET /api/ai/asset/feedback/{questionId}/{assetType}
```

**响应**（有反馈时）：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "helpful": true,
    "comment": "解释得很清楚"
  }
}
```

**响应**（无反馈时）：`data` 为 `null`。

#### 10.5.7 记录资产查看

```
POST /api/ai/asset/view
```

**请求体**：
```json
{
  "questionId": 42,
  "assetType": "FULL_EXPLANATION"
}
```

仅当对应题目的该类缓存资产真实存在时记录。服务端按“用户 + 题目 + 资产类型 + 日期”聚合查看次数，前端只在内容实际可见时调用；该数据用于学习效果观察，不计入 AI 调用配额。

当 `assetType=VARIANT` 时，该接口还会为当前缓存资产版本幂等创建变式训练开始记录，并在 `data` 返回 `assetId`、`status`、`completed`、`startedTime` 和 `completedTime`；其他资产类型的 `data` 为 `null`。

#### 10.5.8 显式确认变式训练完成

```
POST /api/ai/variant-training/{questionId}/complete
```

仅用于兼容 V19 前的 Markdown 变式题：允许完成当前缓存版本且已经进入可见状态的训练，重复调用保持幂等。结构化变式题会拒绝该接口，必须提交答案完成真实判分。

#### 10.5.9 提交结构化变式题答案

```
POST /api/ai/variant-training/{questionId}/answer
```

**请求体**：
```json
{ "userAnswer": "B" }
```

服务端只判定当前缓存资产版本，并要求用户已通过可见性上报开始训练。首版结构化变式题固定为单选题，复用平台 `AnswerEvaluator` 判分。首次提交会写入 `userAnswer`、`isCorrect`、`answeredTime`，同时将训练更新为 `COMPLETED`；重复提交只返回首次结果，不覆盖样本。响应在 `answered=true` 后包含 `correct`、`userAnswer`、`correctAnswer`、`analysis` 和时间字段。

---

## 十一、统计接口

### 11.1 用户学习统计

```
GET /api/statistics/overview
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalPractice": 500,
    "correctCount": 350,
    "correctRate": 0.70,
    "wrongCount": 150,
    "todayPractice": 20,
    "streakDays": 7
  }
}
```

### 11.2 最近学习趋势

```
GET /api/statistics/trend?days=7
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "date": "2024-01-01", "practiceCount": 20, "correctRate": 0.75 },
    { "date": "2024-01-02", "practiceCount": 15, "correctRate": 0.80 }
  ]
}
```

### 11.3 知识点掌握情况

```
GET /api/statistics/knowledge-mastery
```

### 11.4 管理端数据总览

```
GET /api/admin/statistics/overview
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalUsers": 100,
    "enabledUsers": 96,
    "totalQuestions": 5000,
    "weeklyNewQuestions": 35,
    "totalExamPapers": 50,
    "publishedExamPapers": 42,
    "draftExamPapers": 8,
    "todayActiveUsers": 25,
    "totalPracticeRecords": 12000,
    "questionTypeDistribution": {
      "单选题": 2400,
      "多选题": 900,
      "判断题": 800,
      "填空题": 500,
      "简答题": 400
    },
    "dailyActivity": [
      { "date": "2026-06-07", "practiceCount": 120, "activeUsers": 18 }
    ]
  }
}
```

仅管理员可访问。今日活跃用户按当天产生刷题记录的去重用户数统计。

### 11.5 个人学习报告

```
GET /api/statistics/learning-report
```

返回当前用户月度学习报告，包含本月刷题量、正确率、错题、考试、复习统计与学习效果指标。

学习效果指标字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| correctRateChange | Double | 本月正确率较上月变化，单位为百分点 |
| learningEffectScore | Double | 学习效果综合分，0-100 |
| learningEffectLevel | String | `EXCELLENT` / `IMPROVING` / `STABLE` / `AT_RISK` |
| learningEffectLabel | String | 学习效果等级文案 |
| learningEffectSummary | String | 学习效果建议摘要 |
| wrongQuestionConversionRate | Double | 错题转化率，已掌握错题 / 新增错题与已掌握错题合计 |
| reviewMasteryRate | Double | 复习掌握率，已掌握复习卡片 / 复习卡片总数 |
| activeStudyDays | Integer | 本月活跃学习天数 |

---

## 十二、管理端接口

### 12.1 用户管理

```
GET    /api/admin/users              # 用户列表
PUT    /api/admin/users/{id}/role    # 修改角色
PUT    /api/admin/users/{id}/status  # 启用/禁用
PUT    /api/admin/users/{id}/ai-daily-quota # 设置用户 AI 日配额
GET    /api/admin/users/{id}/ai-daily-quota/audits # 查询配额调整审计记录
```

`PUT /api/admin/users/{id}/ai-daily-quota` 请求体为 `{ "dailyQuota": 100, "reason": "按学习计划提升配额" }`。`dailyQuota` 可为 `0-10000` 的整数；`0` 表示该用户不限次数，`null` 表示清除覆盖值并继承服务端 `ai.daily-quota` 全局配置。`reason` 必填、最长 500 字符；服务端会原子更新配额并记录操作管理员、调整前后值、原因和时间。仅管理员可调用。

`GET /api/admin/users/{id}/ai-daily-quota/audits?page=1&size=10` 返回该用户的配额调整记录，按时间倒序分页。响应记录包含 `adminUserId`、`previousDailyQuota`、`newDailyQuota`、`reason` 与 `createTime`。

### 12.2 课程管理

```
GET    /api/admin/courses            # 课程列表
POST   /api/admin/courses            # 创建课程
PUT    /api/admin/courses/{id}       # 更新课程
DELETE /api/admin/courses/{id}       # 删除课程
```

**创建/更新请求体**：
```json
{
  "name": "string",
  "description": "string",
  "coverImage": "string",
  "sortOrder": 0,
  "status": 1
}
```

### 12.3 知识点管理

```
GET    /api/admin/knowledge-points?courseId={id}   # 知识点列表
POST   /api/admin/knowledge-points                 # 创建知识点
PUT    /api/admin/knowledge-points/{id}            # 更新知识点
DELETE /api/admin/knowledge-points/{id}            # 删除知识点
```

### 12.4 题目管理

```
GET    /api/admin/questions          # 题目列表（支持筛选）
GET    /api/admin/questions/duplicates # 疑似重复题目检测
GET    /api/admin/questions/correction-reports # 题目纠错反馈列表
POST   /api/admin/questions/correction-reports/{reportId}/process # 处理题目纠错反馈
GET    /api/admin/questions/{id}/versions # 题目版本记录
POST   /api/admin/questions          # 创建题目
PUT    /api/admin/questions/{id}     # 更新题目
DELETE /api/admin/questions/{id}     # 删除题目
PUT    /api/admin/questions/{id}/status  # 启用/禁用
```

**创建题目请求体**：
```json
{
  "content": "题目内容（支持Markdown）",
  "questionType": "SINGLE_CHOICE",
  "courseId": 1,
  "difficulty": 3,
  "analysis": "解析内容",
  "tags": "标签1,标签2",
  "score": 5,
  "knowledgePointIds": [1, 2],
  "options": [
    { "optionLabel": "A", "content": "选项A", "isCorrect": false },
    { "optionLabel": "B", "content": "选项B", "isCorrect": true },
    { "optionLabel": "C", "content": "选项C", "isCorrect": false },
    { "optionLabel": "D", "content": "选项D", "isCorrect": false }
  ]
}
```

**疑似重复题目检测**：

```
GET /api/admin/questions/duplicates
```

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| courseId | long | 否 | | 仅检测指定课程 |
| questionType | string | 否 | | 仅检测指定题型 |
| minSimilarity | int | 否 | 92 | 相似度阈值，后端限制在 70-100 |
| limit | int | 否 | 20 | 最多返回分组数，后端限制在 1-50 |

响应示例：

```json
[
  {
    "matchType": "EXACT",
    "similarityScore": 100,
    "representativeContent": "Java 中 == 和 equals 有什么区别？",
    "questions": [
      { "id": 1, "content": "Java 中 == 和 equals 有什么区别？", "questionType": "SHORT_ANSWER" },
      { "id": 2, "content": "Java中==和equals有什么区别", "questionType": "SHORT_ANSWER" }
    ]
  }
]
```

说明：`matchType` 取值为 `EXACT`、`SIMILAR`。检测会按课程和题型分桶，只提示疑似重复题目，不会自动合并、删除或修改题库内容。

题目纠错反馈：

```
GET  /api/admin/questions/correction-reports?status=OPEN&questionId=1
POST /api/admin/questions/correction-reports/{reportId}/process
```

处理请求体：

```json
{
  "status": "RESOLVED",
  "handlerComment": "已核对题目并修正解析。"
}
```

说明：`status` 取值为 `OPEN`、`RESOLVED`、`REJECTED`。该接口只记录处理状态、处理人、处理说明和处理时间；若需要修改正式题目内容，仍应使用题目编辑或复审流程完成。

题目版本记录：

```
GET /api/admin/questions/{id}/versions
```

响应示例：

```json
[
  {
    "id": 1,
    "questionId": 10,
    "versionNo": 2,
    "changeType": "UPDATE",
    "operatorId": 1,
    "operatorName": "管理员",
    "changeSummary": "更新题目内容、选项或知识点",
    "snapshotBefore": "{\"content\":\"旧题干\"}",
    "snapshotAfter": "{\"content\":\"新题干\"}",
    "createTime": "2026-07-03T12:00:00"
  }
]
```

说明：`changeType` 取值为 `CREATE`、`UPDATE`、`DELETE`、`REVIEW_APPROVE`、`REVIEW_REVISE`、`REVIEW_REJECT`。快照保存题目核心字段、选项和知识点 ID，用于审计追踪；当前仅支持查看，不提供自动回滚。

正式题目来源与复审接口：

```
GET  /api/admin/questions/source-stats              # 题目来源统计
GET  /api/admin/questions/source-types              # 来源类型列表
GET  /api/admin/questions/review-overdue            # 待复审题目列表
GET  /api/admin/questions/{id}/review-records       # 题目复审记录
GET  /api/admin/questions/{id}/review-suggestion    # AI 复审建议（按题目缓存）
POST /api/admin/questions/{id}/re-review            # 执行复审
```

AI 复审建议响应摘要：

```json
{
  "recommendation": "REVISE",
  "confidenceScore": 88,
  "summary": "题目可用但解析可增强",
  "suggestedContent": "修订后的建议题干",
  "suggestedDifficulty": 3,
  "riskPoints": ["解析略短"],
  "suggestions": ["补充错误选项说明"],
  "answerAnalysis": "答案与选项一致",
  "knowledgeAnalysis": "知识点匹配当前课程"
}
```

说明：`recommendation` 取值为 `APPROVE`、`REVISE`、`REJECT`。该接口只提供审核辅助建议，不直接修改题目；结果进入 `questionReviewSuggestion` 缓存，题目编辑、删除或正式提交复审后会清除对应缓存。

### 12.5 试卷管理

```
GET    /api/admin/exam-papers        # 试卷列表
POST   /api/admin/exam-papers        # 创建试卷
PUT    /api/admin/exam-papers/{id}   # 更新试卷
DELETE /api/admin/exam-papers/{id}   # 删除试卷
POST   /api/admin/exam-papers/{id}/publish  # 发布试卷
POST   /api/admin/exam-papers/{id}/random-compose  # 随机组卷
POST   /api/admin/exam-papers/{id}/manual-compose  # 手动组卷
```

**随机组卷请求体**：
```json
{
  "courseId": 1,
  "knowledgePointId": null,
  "questionTypes": ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE"],
  "difficulty": null,
  "count": 20,
  "scorePerQuestion": 5
}
```

**手动组卷请求体**：
```json
{
  "questionIds": [1, 2, 3, 4, 5],
  "scores": [5, 5, 5, 5, 5]
}
```

### 12.6 题目投稿管理（Phase 16）

用户端投稿接口：

```
POST /api/submission              # 提交题目投稿
GET  /api/submission/my           # 我的投稿列表（pageNum/pageSize/status）
GET  /api/submission/{id}         # 投稿详情
```

管理端投稿接口：

```
GET  /api/admin/submission              # 投稿列表（pageNum/pageSize/status/courseId/keyword）
GET  /api/admin/submission/{id}         # 投稿详情
POST /api/admin/submission/{id}/review  # 审核投稿（通过/拒绝）
POST /api/admin/submission/{id}/import  # 将已通过投稿入库为正式题目
GET  /api/admin/submission/stats        # 投稿状态统计
```

投稿请求体摘要：

```json
{
  "content": "题干内容",
  "questionType": "SINGLE_CHOICE",
  "courseId": 1,
  "difficulty": 3,
  "analysis": "解析内容",
  "optionsJson": "[{\"content\":\"选项A\",\"label\":\"A\",\"isCorrect\":true}]",
  "correctAnswer": "TRUE 或 填空/简答参考答案",
  "knowledgePointIds": "1,2",
  "tags": "标签1,标签2",
  "source": "题目来源"
}
```

审核请求体：

```json
{
  "status": 1,
  "reviewComment": "审核意见"
}
```

说明：
- 投稿状态：`0` 待审核，`1` 已通过，`2` 已拒绝，`3` 已入库。
- 选择题投稿必须提供不少于 2 个选项；单选题只能有 1 个正确答案，多选题至少 1 个正确答案。
- 判断题投稿使用 `correctAnswer=TRUE/FALSE`，服务端会规范化为“正确/错误”两个正式选项。
- 填空题和简答题投稿必须提供 `correctAnswer`，入库后会以 `ANSWER` 选项写入正式题目选项表，供刷题判分统一读取。

### 13. 间隔重复复习（Phase 17）

以下接口均要求登录，复习调度基于 SM-2 算法维护：

```
GET    /api/review/stats                         # 复习统计概览
GET    /api/review/due?courseId=&limit=           # 今日待复习卡片
GET    /api/review/cards?courseId=                # 全部复习卡片
POST   /api/review/add/{questionId}               # 加入复习计划
POST   /api/review/sync-wrong-questions           # 同步未掌握/部分掌握错题
POST   /api/review/submit                         # 提交复习结果并更新 SM-2 调度
DELETE /api/review/remove/{questionId}            # 移出复习计划
POST   /api/review/reset/{questionId}             # 重置复习进度
POST   /api/review/ai-suggestion                  # AI 复习建议（同步）
POST   /api/review/ai-suggestion/stream           # AI 复习建议（SSE）
```

`POST /api/review/submit` 请求体：

```json
{
  "questionId": 1,
  "userAnswer": "用户答案",
  "answerTime": 30,
  "selfAssessedQuality": 4
}
```

`selfAssessedQuality` 可选，取值 0-5，代表本次回忆质量；未传时由系统根据判分结果映射。接口返回更新后的复习卡片和下次复习时间。

### 14. 全局搜索与快捷导航（Phase 18）

以下接口均要求登录：

```
GET    /api/search?keyword=Java&limit=5            # 跨题目/课程/知识点搜索
GET    /api/search/suggestions                     # 当前用户历史 + 热门关键词
DELETE /api/search/history                         # 清空当前用户搜索历史
DELETE /api/search/history/item?keyword=Java      # 删除一条搜索历史
```

搜索结果按 `questions`、`courses`、`knowledgePoints` 分组返回；每类默认最多 5 条、最大 20 条。搜索时自动记录当前用户历史与全局热门关键词。

### 15. AI 调用分析（Phase 19，管理员）

```
GET /api/admin/ai-usage/overview?days=30
```

返回最近指定天数内的 AI 调用总览，包含全局成功/失败统计、Tokens、平均耗时、每日趋势、按功能/模型分布、Top 活跃用户和最近失败调用。`Tokens` 聚合自上游响应明确返回的 `usage.total_tokens`；`totalCostUsd`、`todayCostUsd` 及各分组的 `totalCostUsd` 仅累计同时具备真实输入/输出 token 与已配置模型单价的调用。未返回 usage、缺少输入/输出拆分或未配置价格的调用成本均为 `null`，不会以字符数或默认价格估算。最近失败调用会返回 `traceId`、`promptTemplate`、`promptHash`、`modelConfigVersion` 等追踪字段；其中 `promptHash` 为 system/user prompt 的 SHA-256 指纹，不能反推原始提示词。用户查询 `GET /api/ai/usage` 与实际调用限流均优先采用其 `aiDailyQuota` 覆盖值；该字段为 `null` 时继承 `ai.daily-quota`，为 `0` 时不限次数。管理端接口位于 `/api/admin/**`，仅 ADMIN 可访问。

```
GET /api/admin/ai-usage/report?days=7
```

返回当前统计周期与前一等长周期的运营报告；`days` 缺省为 7，最大为 90。报告包含调用量、失败率、真实 Tokens、平均耗时、已知成本及环比变化。环比的前一周期为零或成本未知时返回 `null`，不会虚构百分比。`alerts` 基于调用日志推导：当前周期至少 5 次调用且失败率不低于 10%、失败率较前一周期翻倍、平均耗时不低于 5 秒且较前一周期升高至少 50%，以及调用量翻倍且至少增加 10 次。

报告生成的异常提醒会持久化为未确认记录：同类型、同周期天数、同一天生成的未确认提醒会更新同一条记录，返回的 `alerts` 包含 `id`、`status`、`periodStart` 和 `periodEnd`。管理员可确认提醒，系统记录确认人和确认时间。若开启 `AI_ALERT_WEBHOOK_ENABLED=true` 并配置 `AI_ALERT_WEBHOOK_URL`，系统仅在新提醒创建时发送一次结构化 webhook payload；复用当天未确认提醒只更新站内记录，不重复站外通知，webhook 失败只写日志、不阻断报告生成。

```
GET /api/admin/ai-usage/learning-effect?days=30
```

返回 AI 学习资产的真实查看覆盖、用户反馈、后续同题作答和知识点跨题迁移对照。`days` 缺省为 30，限制为 1-90。核心字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| assetViewCount | Long | 周期内按日聚合的资产查看次数 |
| engagedUserCount | Long | 周期内查看资产的去重用户数 |
| viewedQuestionCount | Long | 周期内被查看资产的去重题目数 |
| helpfulRate | Double/null | 周期内反馈的“有帮助”占比，单位为百分比 |
| minimumComparisonSample | Long | 同题、跨题和单资产类型任一对照组输出方向性状态的最小作答数，当前为 5 |
| minimumDistinctUsers | Long | 上述对照组与单个变式难度档输出方向性/就绪状态所需的最少去重学习者数，当前为 3 |
| variantTrainingStartedCount | Long | 周期内首次进入可见状态的变式训练数，按用户和缓存资产版本去重 |
| variantTrainingCompletedCount | Long | 上述周期开始队列中已显式确认完成的训练数 |
| variantTrainingCompletionRate | Double/null | 完成数除以开始数，单位为百分比；无开始记录时为 `null` |
| variantTrainingAnsweredCount | Long | 上述周期开始队列中完成服务端首次判分的结构化变式训练数 |
| variantTrainingCorrectCount | Long | 首次判分正确的结构化变式训练数 |
| variantTrainingCorrectRate | Double/null | 正确数除以首次判分数，单位为百分比；无判分记录时为 `null` |
| variantDifficultyMinimumSample | Long | 单个难度档进入分层观察的最小首次判分样本数，当前为 5 |
| variantDifficultyCoveredCount | Long | 周期内至少有 1 条首次判分的难度档数量 |
| variantDifficultySufficientCount | Long | 周期内达到最小样本门槛的难度档数量 |
| variantDifficultyReadiness | String | `INSUFFICIENT_DATA` / `READY`；至少两个难度档达标后才为 `READY` |
| variantDifficultyConclusion | String | 仅描述难度样本覆盖与分层就绪度，不解释学习效果 |
| variantDifficultyStats | Array | 固定返回难度 1-5 的首次判分数、去重学习者数、正确数、正确率和样本是否达标 |
| afterViewPracticeCount | Long | 同一用户阅读同题资产后产生的作答样本数 |
| afterViewUserCount | Long | 阅读后同题作答组的去重学习者数 |
| afterViewCorrectRate | Double/null | 阅读后同题作答正确率，单位为百分比 |
| baselinePracticeCount | Long | 未发生同题阅读前或从未阅读时的作答样本数 |
| baselineUserCount | Long | 同题对照组的去重学习者数 |
| baselineCorrectRate | Double/null | 对照组作答正确率，单位为百分比 |
| correctRateLift | Double/null | 阅读后组减对照组，单位为百分点 |
| conclusionLevel | String | `INSUFFICIENT_DATA` / `POSITIVE_ASSOCIATION` / `NO_CLEAR_DIFFERENCE` / `NEEDS_ATTENTION` |
| crossQuestionWindowDays | Integer | 跨题迁移相关阅读前后观察窗口，当前固定为 30 天 |
| crossQuestionAfterViewPracticeCount | Long | 阅读某题资产后，在窗口内作答另一道共享知识点题目的样本数 |
| crossQuestionAfterViewUserCount | Long | 阅读后跨题组的去重学习者数 |
| crossQuestionAfterViewCorrectRate | Double/null | 阅读后的跨题作答正确率，单位为百分比 |
| crossQuestionBaselinePracticeCount | Long | 首次相关阅读前、同一窗口内的跨题作答样本数；已有更早相关资产暴露者不计入 |
| crossQuestionBaselineUserCount | Long | 阅读前跨题对照组的去重学习者数 |
| crossQuestionBaselineCorrectRate | Double/null | 阅读前跨题对照组正确率，单位为百分比 |
| crossQuestionCorrectRateLift | Double/null | 阅读后跨题组减阅读前跨题组，单位为百分点 |
| crossQuestionConclusionLevel | String | 跨题观察结论，枚举与 `conclusionLevel` 相同 |
| crossQuestionConclusion | String | 带样本限制和非因果声明的跨题观察说明 |
| assetTypeStats | Array | 各资产类型的查看、用户、反馈、有帮助率，以及按该类型首次查看切分的同题两组样本数、正确率、百分点差异、样本状态和观察说明 |

跨题迁移只匹配同一用户、不同题目且至少共享一个知识点的记录，排除原题重答；阅读后组与阅读前组均使用 30 天窗口，对照组还会排除已有更早相关阅读的用户，降低历史暴露污染。同题、跨题和单资产类型的任一对照组都必须同时达到 5 条作答与 3 位去重学习者，否则对应结论返回 `INSUFFICIENT_DATA`，避免单个高频用户主导方向判断。变式训练完成率兼容旧版显式确认和新版真实判分，但结构化变式正确率与难度分布只来自服务端首次判分，不从完成按钮、AI 生成或调用日志推断。难度分布固定返回 1-5 档，明细额外返回 `answeredUserCount`；每档至少 5 条、3 位学习者且至少两个难度档达标后，`variantDifficultyReadiness` 才返回 `READY`，该状态只表示可以开始分层观察，不代表不同难度已存在效果差异。`assetTypeStats` 对每种周期内出现的资产类型重复同题切分，包含两组作答数、去重学习者数、正确率、百分点差异、样本状态和说明。同一作答可能因用户看过多类资产进入多个类型观察组，因此类型明细不能直接用于资产排名或自动推荐。该接口只描述真实行为中的观察性关联，不将正确率差异解释为 AI 内容造成的因果提升。

```
GET /api/admin/ai-usage/alerts?limit=20
POST /api/admin/ai-usage/alerts/{id}/acknowledge
```

`GET /alerts` 返回未确认的持久化 AI 运营提醒，默认 20 条、最大 100 条；`POST /acknowledge` 将提醒从 `OPEN` 更新为 `ACKNOWLEDGED`。
