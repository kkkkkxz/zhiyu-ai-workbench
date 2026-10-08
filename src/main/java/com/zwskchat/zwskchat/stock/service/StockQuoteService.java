package com.zwskchat.zwskchat.stock.service;

import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockQuote;
import com.zwskchat.zwskchat.stock.provider.StockQuoteProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockQuoteService {

    private final StockProperties props;
    private final List<StockQuoteProvider> providers;
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();

    public Optional<StockQuote> getQuote(StockProperties.Company company) {
        String key = cacheKey(company);
        CachedQuote cached = cache.get(key);
        if (cached != null && !cached.expired(props.getCacheTtlSeconds())) {
            return Optional.of(cached.quote());
        }
        Optional<StockQuote> fresh = loadQuote(company);
        fresh.ifPresent(q -> cache.put(key, new CachedQuote(q, Instant.now())));
        return fresh;
    }

    private Optional<StockQuote> loadQuote(StockProperties.Company company) {
        Map<String, StockQuoteProvider> byName = providers.stream()
                .collect(Collectors.toMap(p -> p.name().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        String providerName = props.getProvider() == null ? "auto" : props.getProvider().trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(providerName)) {
            Optional<StockQuote> cn = call(byName.get("tencent-finance"), company);
            if (cn.isPresent()) return cn;
            if (!isBlank(props.getApiKey())) {
                Optional<StockQuote> real = call(byName.get("twelve-data"), company);
                if (real.isPresent()) return real;
            }
            return call(byName.get("mock"), company);
        }
        return call(byName.get(providerName), company);
    }

    private Optional<StockQuote> call(StockQuoteProvider provider, StockProperties.Company company) {
        if (provider == null) return Optional.empty();
        try {
            return provider.getQuote(company);
        } catch (Exception e) {
            log.warn("[Stock] provider {} failed for {}: {}", provider.name(), company.getSymbol(), e.getMessage());
            return Optional.empty();
        }
    }

    private String cacheKey(StockProperties.Company company) {
        String symbol = firstNonBlank(company.getProviderSymbol(), company.getSymbol());
        return symbol == null ? company.getName() : symbol;
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record CachedQuote(StockQuote quote, Instant cachedAt) {
        boolean expired(int ttlSeconds) {
            int ttl = Math.max(1, ttlSeconds);
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= ttl;
        }
    }
}
