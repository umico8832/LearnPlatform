package com.learnplatform.dto;

import com.learnplatform.entity.AiCallLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AiCallLogVO(
        Long id,
        Long userId,
        String functionType,
        String model,
        Integer tokensUsed,
        Integer promptTokens,
        Integer completionTokens,
        BigDecimal costUsd,
        Integer status,
        String errorMessage,
        Integer duration,
        String traceId,
        String promptTemplate,
        String promptHash,
        String modelConfigVersion,
        LocalDateTime createTime
) {
    public static AiCallLogVO fromEntity(AiCallLog entity) {
        return new AiCallLogVO(
                entity.getId(),
                entity.getUserId(),
                entity.getFunctionType(),
                entity.getModel(),
                entity.getTokensUsed(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getCostUsd(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getDuration(),
                entity.getTraceId(),
                entity.getPromptTemplate(),
                entity.getPromptHash(),
                entity.getModelConfigVersion(),
                entity.getCreateTime()
        );
    }
}
