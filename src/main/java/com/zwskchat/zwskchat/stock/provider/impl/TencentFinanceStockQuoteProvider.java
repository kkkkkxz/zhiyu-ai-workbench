package com.zwskchat.zwskchat.stock.provider.impl;

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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TencentFinanceStockQuoteProvider implements StockQuoteProvider {

    private static final Charset GBK = Charset.forName("GBK");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("v_[^=]+=\"([^\"]*)\"");

    private final StockProperties props;
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
        return "tencent-finance";
    }

    @Override
    public Optional<StockQuote> getQuote(StockProperties.Company company) {
        String quoteSymbol = toTencentSymbol(company);
        if (quoteSymbol == null || quoteSymbol.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create("https://qt.gtimg.cn/q=" + enc(quoteSymbol));
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, props.getRequestTimeoutSeconds())))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("[Stock] Tencent Finance HTTP {} for {}", resp.statusCode(), quoteSymbol);
                return Optional.empty();
            }

            String body = new String(resp.body(), GBK);
            Matcher matcher = QUOTE_PATTERN.matcher(body);
            if (!matcher.find()) {
                log.warn("[Stock] Tencent Finance response has no quote for {}", quoteSymbol);
                return Optional.empty();
            }

            String[] parts = matcher.group(1).split("~", -1);
            if (parts.length < 33 || isBlank(parts[3])) {
                log.warn("[Stock] Tencent Finance response is incomplete for {}", quoteSymbol);
                return Optional.empty();
            }

            BigDecimal price = decimal(parts, 3).orElse(null);
            if (price == null) return Optional.empty();

            BigDecimal change = decimal(parts, 31).orElse(null);
            BigDecimal pct = decimal(parts, 32).orElse(null);
            Long volume = longValue(parts, 36).or(() -> longValue(parts, 6)).orElse(null);
            String marketTime = value(parts, 30).orElse(null);
            String name = value(parts, 1).orElse(company.getName());
            String market = isBlank(company.getMarket()) ? inferMarket(quoteSymbol) : company.getMarket();
            String currency = isBlank(company.getCurrency()) ? inferCurrency(quoteSymbol) : company.getCurrency();

            return Optional.of(new StockQuote(
                    name,
                    firstNonBlank(company.getSymbol(), value(parts, 2).orElse(quoteSymbol)),
                    market,
                    currency,
                    price,
                    change,
                    pct,
                    volume,
                    marketTime,
                    Instant.now(),
                    "腾讯财经",
                    true,
                    false
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[Stock] Tencent Finance quote failed for {}: {}", quoteSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    private static String toTencentSymbol(StockProperties.Company company) {
        String raw = firstNonBlank(company.getProviderSymbol(), company.getSymbol());
        if (isBlank(raw)) return null;
        String s = raw.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.matches("^(hk|us|sh|sz)[a-z0-9.]+$")) {
            return lower.startsWith("us") ? "us" + s.substring(2).toUpperCase(Locale.ROOT) : lower;
        }

        String market = company.getMarket() == null ? "" : company.getMarket().trim().toUpperCase(Locale.ROOT);
        String symbol = company.getSymbol() == null ? s : company.getSymbol().trim();
        String normalized = s.toUpperCase(Locale.ROOT);

        if ("HKEX".equals(market) || normalized.endsWith(".HK") || symbol.toUpperCase(Locale.ROOT).endsWith(".HK")) {
            String digits = normalized.replace(".HK", "").replaceAll("\\D", "");
            if (digits.isBlank()) return null;
            return "hk" + leftPad(digits, 5);
        }
        if ("SSE".equals(market) || "SH".equals(market) || normalized.startsWith("SH")) {
            String digits = normalized.replaceFirst("^SH", "").replaceAll("\\D", "");
            return digits.isBlank() ? null : "sh" + leftPad(digits, 6);
        }
        if ("SZSE".equals(market) || "SZ".equals(market) || normalized.startsWith("SZ")) {
            String digits = normalized.replaceFirst("^SZ", "").replaceAll("\\D", "");
            return digits.isBlank() ? null : "sz" + leftPad(digits, 6);
        }
        if (normalized.matches("^[036]\\d{5}$")) {
            return normalized.startsWith("6") ? "sh" + normalized : "sz" + normalized;
        }
        return "us" + normalized.replaceAll("\\..*$", "");
    }

    private static Optional<BigDecimal> decimal(String[] parts, int index) {
        return value(parts, index).flatMap(v -> {
            try {
                return Optional.of(new BigDecimal(v));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Long> longValue(String[] parts, int index) {
        return decimal(parts, index).map(BigDecimal::longValue);
    }

    private static Optional<String> value(String[] parts, int index) {
        if (index < 0 || index >= parts.length) return Optional.empty();
        String v = parts[index];
        return isBlank(v) ? Optional.empty() : Optional.of(v.trim());
    }

    private static String inferMarket(String quoteSymbol) {
        if (quoteSymbol.startsWith("hk")) return "HKEX";
        if (quoteSymbol.startsWith("sh")) return "SSE";
        if (quoteSymbol.startsWith("sz")) return "SZSE";
        if (quoteSymbol.startsWith("us")) return "US";
        return "";
    }

    private static String inferCurrency(String quoteSymbol) {
        if (quoteSymbol.startsWith("hk")) return "HKD";
        if (quoteSymbol.startsWith("sh") || quoteSymbol.startsWith("sz")) return "CNY";
        if (quoteSymbol.startsWith("us")) return "USD";
        return "";
    }

    private static String leftPad(String value, int width) {
        if (value.length() >= width) return value;
        return "0".repeat(width - value.length()) + value;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
