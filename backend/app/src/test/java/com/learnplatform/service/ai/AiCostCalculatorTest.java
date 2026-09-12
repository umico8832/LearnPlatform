package com.learnplatform.service.ai;

import com.learnplatform.config.AiConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiCostCalculatorTest {

    @Test
    void embeddingCostNeedsOnlyExplicitInputUsageAndInputPrice() {
        var price = new AiCallTicket.Price(new java.math.BigDecimal("0.1"), null);
        var usage = new com.learnplatform.ai.model.ModelResult.Usage(1000, null, 1000);
        assertEquals(new java.math.BigDecimal("0.00010000"), AiCostCalculator.calculate(price, usage, true));
        assertNull(AiCostCalculator.calculate(price, usage, false));
        assertNull(AiCostCalculator.calculate(price, null, true));
    }

    @Test
    void calculatesCostFromConfiguredInputAndOutputPrices() {
        AiConfig config = new AiConfig();
        AiConfig.ModelPrice price = new AiConfig.ModelPrice();
        price.setInputPerMillion(new BigDecimal("0.15"));
        price.setOutputPerMillion(new BigDecimal("0.60"));
        config.setModelPrices(Map.of("gpt-4o-mini", price));

        BigDecimal result = new AiCostCalculator(config)
                .calculate("gpt-4o-mini", new AiTokenUsage(1_000, 2_000, 3_000));

        assertEquals(new BigDecimal("0.00135000"), result);
    }

    @Test
    void doesNotCalculateWithoutSeparateUpstreamUsageOrPrice() {
        AiConfig config = new AiConfig();

        AiCostCalculator calculator = new AiCostCalculator(config);

        assertNull(calculator.calculate("unknown", new AiTokenUsage(10, 20, 30)));
        assertNull(calculator.calculate("unknown", new AiTokenUsage(null, null, 30)));
    }
}
