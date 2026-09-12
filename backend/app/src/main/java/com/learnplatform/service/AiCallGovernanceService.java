package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.ai.AiCallTicket;
import com.learnplatform.service.ai.AiCostCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class AiCallGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(AiCallGovernanceService.class);
    private final AiConfig config;
    private final AiCallLogMapper logs;
    private final UserMapper users;
    private final AiCallReservationService reservations;
    private final ObjectMapper json;

    public AiCallGovernanceService(AiConfig config, AiCallLogMapper logs, UserMapper users,
                                   AiCallReservationService reservations, ObjectMapper json) {
        this.config = config;
        this.logs = logs;
        this.users = users;
        this.reservations = reservations;
        this.json = json;
    }

    public AiCallTicket begin(AiCallContext context, ModelRequest request) {
        var entry = new AiCallLog();
        entry.setUserId(context.userId());
        entry.setFunctionType(context.function());
        entry.setCallId(UUID.randomUUID().toString());
        entry.setRunId(context.runId() == null ? null : context.runId().toString());
        entry.setCallKind("CHAT");
        entry.setRequestedModel(request.options().model());
        entry.setModel(request.options().model());
        entry.setOutcome("RUNNING");
        entry.setStatus(0);
        entry.setTraceId(MDC.get("traceId"));
        entry.setPromptTemplate(context.function());
        Map<String, AiCallTicket.Price> prices = new HashMap<>();
        config.getModelPrices().forEach((model, price) -> prices.put(model,
                new AiCallTicket.Price(price.getInputPerMillion(), price.getOutputPerMillion())));
        entry.setPromptHash(hash(serialize(request.messages())));
        entry.setModelConfigVersion(hash(serialize(request.options()) + serialize(request.tools())
                + serialize(request.outputSchema()) + serialize(new java.util.TreeMap<>(prices))
                + config.getApiBaseUrl() + config.isStreamIncludeUsage()
                + config.isToolsSupported() + config.isStructuredOutputSupported()));
        reservations.reserve(entry);
        return new AiCallTicket(entry, prices);
    }

    public void finish(AiCallTicket ticket, ModelResult result, String outcome, long duration) {
        AiCallLog entry = ticket.entry();
        entry.setOutcome(outcome);
        entry.setStatus("SUCCEEDED".equals(outcome) ? 1 : 0);
        entry.setErrorMessage("SUCCEEDED".equals(outcome) ? null : "AI_" + outcome);
        entry.setDuration((int) Math.min(Integer.MAX_VALUE, Math.max(0, duration)));
        if (result != null) {
            if (result.model() != null && !result.model().isBlank()) {
                entry.setModel(result.model());
            }
            entry.setResponseId(result.responseId());
            entry.setFinishReason(result.finish() == null ? null : result.finish().name());
            if (result.usage() != null) {
                entry.setPromptTokens(result.usage().inputTokens());
                entry.setCompletionTokens(result.usage().outputTokens());
                entry.setTokensUsed(result.usage().totalTokens());
                entry.setCostUsd(AiCostCalculator.calculate(
                        ticket.prices().get(entry.getModel()), result.usage(), false));
            }
        }
        try {
            reservations.finish(entry);
        } catch (RuntimeException exception) {
            // The committed reservation preserves quota accounting if completion auditing is unavailable.
            log.error("AI completion audit unavailable: callId={}", entry.getCallId());
        }
    }

    public void checkDailyQuota(Long userId) {
        int limit = resolveDailyQuota(userId);
        if (limit > 0 && countTodayCalls(userId) >= limit) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED,
                    "今日 AI 调用次数已达上限（" + limit + " 次），请明天再试");
        }
    }

    public long countTodayCalls(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long count = logs.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getUserId, userId).ge(AiCallLog::getCreateTime, start));
        return count == null ? 0 : count;
    }

    public int[] getDailyUsage(Long userId) {
        return new int[]{(int) countTodayCalls(userId), resolveDailyQuota(userId)};
    }

    private int resolveDailyQuota(Long userId) {
        User user = users.selectById(userId);
        return user != null && user.getAiDailyQuota() != null ? user.getAiDailyQuota() : config.getDailyQuota();
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI request cannot be fingerprinted");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
