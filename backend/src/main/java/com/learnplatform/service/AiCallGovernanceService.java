package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.ai.AiCostCalculator;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiTokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 调用治理服务：统一处理用户配额、调用审计、Token 成本与提示词配置指纹。
 */
@Service
public class AiCallGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(AiCallGovernanceService.class);

    private final AiProvider aiProvider;
    private final AiCostCalculator aiCostCalculator;
    private final AiConfig aiConfig;
    private final AiCallLogMapper aiCallLogMapper;
    private final UserMapper userMapper;

    public AiCallGovernanceService(AiProvider aiProvider,
                                   AiCostCalculator aiCostCalculator,
                                   AiConfig aiConfig,
                                   AiCallLogMapper aiCallLogMapper,
                                   UserMapper userMapper) {
        this.aiProvider = aiProvider;
        this.aiCostCalculator = aiCostCalculator;
        this.aiConfig = aiConfig;
        this.aiCallLogMapper = aiCallLogMapper;
        this.userMapper = userMapper;
    }

    /** 检查用户每日 AI 调用配额，超限时拒绝本次调用。 */
    public void checkDailyQuota(Long userId) {
        int dailyQuota = resolveDailyQuota(userId);
        if (dailyQuota <= 0) {
            return;
        }

        long todayCount = countTodayCalls(userId);
        if (todayCount >= dailyQuota) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED,
                    "今日 AI 调用次数已达上限（" + dailyQuota + " 次），请明天再试");
        }
        log.debug("用户 {} 今日已调用 AI {} 次，配额 {} 次", userId, todayCount, dailyQuota);
    }

    public long countTodayCalls(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCallLog::getUserId, userId)
                .ge(AiCallLog::getCreateTime, todayStart);
        Long count = aiCallLogMapper.selectCount(wrapper);
        return count != null ? count : 0;
    }

    /** @return int[] {todayCount, dailyQuota} */
    public int[] getDailyUsage(Long userId) {
        int dailyQuota = resolveDailyQuota(userId);
        long todayCount = countTodayCalls(userId);
        return new int[]{(int) todayCount, dailyQuota};
    }

    public void logCall(Long userId, String functionType, boolean success, String errorMessage, int duration) {
        saveLog(userId, functionType, success, errorMessage, duration, false, null, null);
    }

    void logCallWithPrompt(Long userId, String functionType, boolean success, String errorMessage,
                           int duration, String systemPrompt, String userPrompt) {
        saveLog(userId, functionType, success, errorMessage, duration, true, systemPrompt, userPrompt);
    }

    private int resolveDailyQuota(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null && user.getAiDailyQuota() != null) {
            return user.getAiDailyQuota();
        }
        return aiConfig.getDailyQuota();
    }

    private void saveLog(Long userId, String functionType, boolean success, String errorMessage,
                         int duration, boolean promptProvided, String systemPrompt, String userPrompt) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setUserId(userId);
            callLog.setFunctionType(functionType);
            callLog.setModel(aiConfig.getModel());
            callLog.setStatus(success ? 1 : 0);
            callLog.setErrorMessage(errorMessage);
            callLog.setDuration(duration);
            callLog.setTraceId(MDC.get("traceId"));
            callLog.setPromptTemplate(functionType);
            callLog.setPromptHash(promptProvided
                    ? sha256("system:" + systemPrompt + "\nuser:" + userPrompt) : null);
            callLog.setModelConfigVersion(modelConfigVersion());
            AiTokenUsage tokenUsage = success ? aiProvider.getLastTokenUsage() : null;
            if (tokenUsage != null) {
                callLog.setTokensUsed(tokenUsage.totalTokens());
                callLog.setPromptTokens(tokenUsage.promptTokens());
                callLog.setCompletionTokens(tokenUsage.completionTokens());
                callLog.setCostUsd(aiCostCalculator.calculate(callLog.getModel(), tokenUsage));
            }
            aiCallLogMapper.insert(callLog);
            log.info("AI 调用日志已记录: type={}, userId={}, success={}, duration={}ms, tokens={}, "
                            + "costUsd={}, traceId={}, modelConfigVersion={}",
                    functionType, userId, success, duration, callLog.getTokensUsed(),
                    callLog.getCostUsd(), callLog.getTraceId(), callLog.getModelConfigVersion());
        } catch (Exception e) {
            // 审计写入失败不应覆盖主调用结果。
            log.warn("AI 调用日志记录失败: {}", e.getMessage());
        }
    }

    private String modelConfigVersion() {
        String model = aiConfig.getModel();
        StringBuilder sb = new StringBuilder();
        sb.append("model=").append(model)
                .append(";maxTokens=").append(aiConfig.getMaxTokens())
                .append(";streamIncludeUsage=").append(aiConfig.isStreamIncludeUsage());
        Map<String, AiConfig.ModelPrice> prices = aiConfig.getModelPrices();
        AiConfig.ModelPrice price = prices == null ? null : prices.get(model);
        if (price != null) {
            sb.append(";inputPerMillion=").append(toPlainString(price.getInputPerMillion()))
                    .append(";outputPerMillion=").append(toPlainString(price.getOutputPerMillion()));
        }
        return sha256(sb.toString());
    }

    private String toPlainString(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
