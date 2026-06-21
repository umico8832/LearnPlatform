package com.learnplatform.service.ai;

/**
 * 上游 AI 服务返回的 token 用量。
 *
 * <p>数值仅在上游响应明确包含 usage 时存在，不以字符数或本地规则估算，
 * 避免把估算值误作为真实成本数据。</p>
 */
public record AiTokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
