# AI 学习 API

## 基础生成

| 接口 | 说明 |
|---|---|
| `POST /api/ai/explanation` | 生成题目解析 |
| `POST /api/ai/explanation/stream` | 流式生成题目解析 |
| `POST /api/ai/variant` | 生成变式题 |
| `POST /api/ai/variant/stream` | 流式生成变式题 |
| `POST /api/ai/review-suggestion` | 生成复习建议 |
| `POST /api/ai/review-suggestion/stream` | 流式生成复习建议 |
| `POST /api/ai/summary` | 生成知识点总结 |
| `GET /api/ai/usage` | 查询当前用户 AI 配额摘要 |

## 学习资产

| 接口 | 说明 |
|---|---|
| `GET /api/ai/assets/{questionId}` | 查询题目已缓存资产 |
| `POST /api/ai/asset/generate` | 同步生成或获取资产 |
| `POST /api/ai/asset/stream` | 流式生成资产 |
| `POST /api/ai/asset/feedback` | 提交资产反馈 |
| `GET /api/ai/asset/feedback/{questionId}/{assetType}` | 查询本人反馈 |
| `POST /api/ai/asset/view` | 记录用户真实查看 |
| `DELETE /api/ai/assets/{questionId}` | 清除当前题目的资产缓存 |

记录查看必须发生在资产实际向用户展示之后，不能把预加载或接口请求直接当成阅读。

## 变式训练

| 接口 | 说明 |
|---|---|
| `POST /api/ai/variant-training/{questionId}/complete` | 兼容旧流程的显式完成 |
| `POST /api/ai/variant-training/{questionId}/answer` | 提交结构化变式题答案并首次判分 |

结构化答案由服务端保存和判分；首次判分结果锁定，重复提交不能改写学习效果样本。

## 流式响应

流式接口返回 `text/event-stream`。客户端需要处理：

- 分片到达和增量渲染；
- 用户主动取消；
- 网络中断和业务错误事件；
- 完成事件后再写入缓存或完成状态。

AI Provider 的原始响应、Token 用量和成本由后端统一归一化，前端不得依赖具体供应商字段。
