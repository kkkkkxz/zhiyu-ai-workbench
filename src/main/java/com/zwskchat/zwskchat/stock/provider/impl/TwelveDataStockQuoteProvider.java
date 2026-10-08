package com.zwskchat.zwskchat.stock.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockQuote;
import com.zwskchat.zwskchat.stock.provider.StockQuoteProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwelveDataStockQuoteProvider implements StockQuoteProvider {

    private final StockProperties props;
    private final ObjectMapper objectMapper;
    private HttpClient http;

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, props.getRequestTimeoutSeconds())))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String name() {
        return "twelve-data";
    }

    @Override
    public Optional<StockQuote> getQuote(StockProperties.Company company) {
        if (isBlank(props.getApiKey())) {
            return Optional.empty();
        }
        String symbol = firstNonBlank(company.getProviderSymbol(), company.getSymbol());
        if (isBlank(symbol)) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(trimRightSlash(props.getBaseUrl())
                    + "/quote?symbol=" + enc(symbol)
                    + "&apikey=" + enc(props.getApiKey()));
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, props.getRequestTimeoutSeconds())))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("[Stock] Twelve Data HTTP {} for {}", resp.statusCode(), symbol);
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(resp.body());
            if (root.hasNonNull("code") && root.hasNonNull("message")
                    && !"200".equals(root.path("code").asText())) {
                log.warn("[Stock] Twelve Data rejected {}: {}", symbol, root.path("message").asText());
                return Optional.empty();
            }

            BigDecimal price = decimal(root, "close")
                    .or(() -> decimal(root, "price"))
                    .or(() -> decimal(root, "previous_close"))
                    .orElse(null);
            if (price == null) {
                log.warn("[Stock] Twelve Data response has no price for {}", symbol);
                return Optional.empty();
            }

            BigDecimal change = decimal(root, "change").orElse(null);
            BigDecimal pct = decimal(root, "percent_change").orElse(null);
            Long volume = longValue(root, "volume").orElse(null);
            String marketTime = firstNonBlank(
                    text(root, "datetime").orElse(null),
                    text(root, "timestamp").orElse(null));
            String name = firstNonBlank(text(root, "name").orElse(null), company.getName());
            String market = firstNonBlank(text(root, "exchange").orElse(null), company.getMarket());
            String currency = firstNonBlank(text(root, "currency").orElse(null), company.getCurrency());

            return Optional.of(new StockQuote(
                    name,
                    firstNonBlank(company.getSymbol(), symbol),
                    market,
                    currency,
                    price,
                    change,
                    pct,
                    volume,
                    marketTime,
                    Instant.now(),
                    name(),
                    props.isDelayed(),
                    false
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[Stock] Twelve Data quote failed for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> decimal(JsonNode root, String field) {
        return text(root, field).flatMap(v -> {
            try {
                return Optional.of(new BigDecimal(v));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Long> longValue(JsonNode root, String field) {
        return text(root, field).flatMap(v -> {
            try {
                return Optional.of(new BigDecimal(v).longValue());
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<String> text(JsonNode root, String field) {
        JsonNode n = root.path(field);
        if (n.isMissingNode() || n.isNull()) return Optional.empty();
        String v = n.asText();
        if (isBlank(v) || "null".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v)) {
            return Optional.empty();
        }
        return Optional.of(v.trim());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String trimRightSlash(String s) {
        if (isBlank(s)) return "https://api.twelvedata.com";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
