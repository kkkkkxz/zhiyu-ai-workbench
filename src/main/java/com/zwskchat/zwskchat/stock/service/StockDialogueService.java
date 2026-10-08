package com.zwskchat.zwskchat.stock.service;

import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockDialogueAnswer;
import com.zwskchat.zwskchat.stock.dto.StockQuery;
import com.zwskchat.zwskchat.stock.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockDialogueService {

    private final StockProperties props;
    private final StockCompanyResolver resolver;
    private final StockQuoteService quoteService;

    public Optional<StockDialogueAnswer> tryAnswer(String question, boolean voice) {
        if (!props.isEnabled()) return Optional.empty();
        StockQuery query = resolver.resolve(question);
        if (!query.stockIntent()) return Optional.empty();
        if (query.ambiguous()) {
            return Optional.of(new StockDialogueAnswer(ambiguousAnswer(voice), List.of()));
        }

        List<StockQuote> quotes = new ArrayList<>();
        int limit = Math.max(1, props.getMaxCompaniesPerAnswer());
        for (StockProperties.Company company : query.companies()) {
            if (quotes.size() >= limit) break;
            quoteService.getQuote(company).ifPresent(quotes::add);
        }

        if (quotes.isEmpty()) {
            return Optional.of(new StockDialogueAnswer(unavailableAnswer(voice), List.of()));
        }
        if (query.rankTopGainer()) {
            quotes.sort(Comparator.comparing(
                    q -> q.changePercent() == null ? BigDecimal.valueOf(-999999) : q.changePercent(),
                    Comparator.reverseOrder()));
        }
        String text = quotes.size() == 1
                ? singleAnswer(quotes.get(0), voice)
                : multiAnswer(quotes, query.rankTopGainer(), voice);
        return Optional.of(new StockDialogueAnswer(text, quotes));
    }

    private String ambiguousAnswer(boolean voice) {
        if (voice) {
            return "你想查哪家公司？可以直接说，苹果现在多少钱，或者腾讯今天涨跌多少。";
        }
        return "你想查哪家公司的股票？可以这样问：苹果现在多少钱、腾讯今天涨跌多少、目标公司谁涨得最多。";
    }

    private String unavailableAnswer(boolean voice) {
        if (voice) {
            return "我暂时查不到这家公司的行情。请确认股票代码，或者稍后再试。";
        }
        return "我暂时查不到这家公司的行情。请确认股票代码/市场配置是否正确，或稍后再试。";
    }

    private String singleAnswer(StockQuote q, boolean voice) {
        String movement = movement(q);
        String source = sourceText(q);
        if (voice) {
            return mockPrefix(q, true)
                    + q.name() + "现在是" + money(q.price(), q.currency())
                    + movement
                    + "。数据时间" + safe(q.marketTime(), "刚刚")
                    + "，" + source + "。";
        }
        return mockPrefix(q, false)
                + q.name() + "（" + q.symbol() + "）当前报 " + money(q.price(), q.currency())
                + movement
                + "。数据时间：" + safe(q.marketTime(), "刚刚")
                + "，来源：" + source
                + "。仅供参考，不构成投资建议。";
    }

    private String multiAnswer(List<StockQuote> quotes, boolean topGainer, boolean voice) {
        StringBuilder sb = new StringBuilder();
        boolean hasMock = quotes.stream().anyMatch(StockQuote::mock);
        if (hasMock) {
            sb.append(voice ? "还没配置真实行情源，下面是示例数据。" : "当前未配置真实行情源，下面是示例行情演示：");
        }
        if (topGainer && !quotes.isEmpty()) {
            StockQuote top = quotes.get(0);
            sb.append("关注公司里涨幅最高的是")
                    .append(top.name()).append("，当前")
                    .append(money(top.price(), top.currency()))
                    .append(movement(top)).append("。");
            if (!voice && quotes.size() > 1) {
                sb.append("其余公司：");
                appendQuoteList(sb, quotes.subList(1, quotes.size()));
            }
        } else {
            sb.append(voice ? "当前关注公司行情：" : "当前关注公司行情：");
            appendQuoteList(sb, quotes);
        }
        if (!voice) {
            sb.append(" 数据时间以各行情源返回为准，仅供参考，不构成投资建议。");
        }
        return sb.toString();
    }

    private void appendQuoteList(StringBuilder sb, List<StockQuote> quotes) {
        for (int i = 0; i < quotes.size(); i++) {
            StockQuote q = quotes.get(i);
            if (i > 0) sb.append("；");
            sb.append(q.name())
                    .append(" ")
                    .append(money(q.price(), q.currency()))
                    .append(movement(q));
        }
        sb.append("。");
    }

    private String mockPrefix(StockQuote q, boolean voice) {
        if (!q.mock()) return "";
        return voice ? "还没配置真实行情源，下面是示例数据。" : "当前未配置真实行情源，下面是示例行情演示：";
    }

    private String movement(StockQuote q) {
        BigDecimal change = q.change();
        BigDecimal pct = q.changePercent();
        if (change == null && pct == null) return "";
        int sign = change != null ? change.signum() : pct.signum();
        if (sign == 0) return "，较前收盘基本持平";
        String verb = sign > 0 ? "，较前收盘上涨" : "，较前收盘下跌";
        StringBuilder sb = new StringBuilder(verb);
        if (change != null) sb.append(money(change.abs(), q.currency()));
        if (pct != null) sb.append("，").append(sign > 0 ? "涨幅" : "跌幅").append(percent(pct.abs()));
        return sb.toString();
    }

    private String sourceText(StockQuote q) {
        if (q.mock()) return "示例行情";
        return q.delayed() ? "延迟行情，来自" + q.source() : "实时行情，来自" + q.source();
    }

    private String money(BigDecimal value, String currency) {
        if (value == null) return "未知价格";
        return number(value) + " " + currencyName(currency);
    }

    private String percent(BigDecimal value) {
        return number(value) + "%";
    }

    private String number(BigDecimal value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        nf.setMaximumFractionDigits(4);
        nf.setMinimumFractionDigits(value.scale() > 0 ? Math.min(value.scale(), 2) : 0);
        return nf.format(value);
    }

    private String currencyName(String currency) {
        if (currency == null || currency.isBlank()) return "";
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "USD" -> "美元";
            case "HKD" -> "港元";
            case "CNY", "RMB" -> "元";
            default -> currency.toUpperCase(Locale.ROOT);
        };
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
