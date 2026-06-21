package com.learnplatform.service.ai;

import com.learnplatform.config.AiConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 根据管理员配置的模型单价计算单次 AI 调用成本。
 *
 * <p>只有上游同时返回输入、输出 token 且该模型配置了正数单价时才返回成本；
 * 其他情况返回 {@code null}，避免把缺失 usage 或未知价格伪装为精确成本。</p>
 */
@Component
public class AiCostCalculator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final AiConfig aiConfig;

    public AiCostCalculator(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    public BigDecimal calculate(String model, AiTokenUsage usage) {
        if (model == null || usage == null || usage.promptTokens() == null || usage.completionTokens() == null) {
            return null;
        }
        AiConfig.ModelPrice price = aiConfig.getModelPrices().get(model);
        if (price == null || !isPositive(price.getInputPerMillion()) || !isPositive(price.getOutputPerMillion())) {
            return null;
        }
        return BigDecimal.valueOf(usage.promptTokens()).multiply(price.getInputPerMillion())
                .add(BigDecimal.valueOf(usage.completionTokens()).multiply(price.getOutputPerMillion()))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
