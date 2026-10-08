package com.zwskchat.zwskchat.stock.provider.impl;

import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockQuote;
import com.zwskchat.zwskchat.stock.provider.StockQuoteProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
public class MockStockQuoteProvider implements StockQuoteProvider {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Optional<StockQuote> getQuote(StockProperties.Company company) {
        String symbol = company.getSymbol() == null ? company.getName() : company.getSymbol();
        int seed = Math.abs(symbol.hashCode());
        long bucket = System.currentTimeMillis() / 60_000L;
        double wave = Math.sin((bucket + seed % 360) / 9.0);
        double base = 80 + (seed % 240);
        BigDecimal previous = BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP);
        BigDecimal change = BigDecimal.valueOf(wave * 3.2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal price = previous.add(change).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pct = previous.signum() == 0
                ? BigDecimal.ZERO
                : change.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
        long volume = 500_000L + (seed % 9_000_000L);
        return Optional.of(new StockQuote(
                company.getName(),
                symbol,
                company.getMarket(),
                company.getCurrency(),
                price,
                change,
                pct,
                volume,
                LocalDateTime.now(SHANGHAI).format(TIME_FMT),
                Instant.now(),
                name(),
                true,
                true
        ));
    }
}
