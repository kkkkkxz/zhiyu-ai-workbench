package com.zwskchat.zwskchat.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockQuote(
        String name,
        String symbol,
        String market,
        String currency,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        Long volume,
        String marketTime,
        Instant receivedAt,
        String source,
        boolean delayed,
        boolean mock
) {
}
