package com.learnplatform.service.ai;

import com.learnplatform.entity.AiCallLog;

import java.math.BigDecimal;
import java.util.Map;

public record AiCallTicket(AiCallLog entry, Map<String, Price> prices) {
    public AiCallTicket {
        prices = Map.copyOf(prices);
    }
    public record Price(BigDecimal input, BigDecimal output) { }
}
