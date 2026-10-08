package com.zwskchat.zwskchat.stock.service;

import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StockCompanyResolver {

    private static final List<String> STRONG_KEYWORDS = List.of(
            "股票", "股价", "行情", "报价", "涨跌", "涨幅", "跌幅", "成交", "开盘", "收盘",
            "市值", "价格", "多少钱", "实时", "quote", "stock", "price", "market", "volume"
    );
    private static final List<String> WEAK_KEYWORDS = List.of(
            "今天", "现在", "当前", "最近", "走势", "表现", "怎么样", "多少", "涨", "跌"
    );
    private static final List<String> TARGET_KEYWORDS = List.of(
            "目标公司", "关注公司", "自选股", "watchlist"
    );
    private static final List<String> TOP_GAINER_KEYWORDS = List.of(
            "涨得最多", "涨最多", "涨幅最大", "领涨", "谁涨"
    );

    private final StockProperties props;

    public StockQuery resolve(String question) {
        String text = normalize(question);
        if (text.isBlank()) return StockQuery.none();

        boolean targetList = containsAny(text, TARGET_KEYWORDS);
        boolean strong = containsAny(text, STRONG_KEYWORDS);
        boolean weak = containsAny(text, WEAK_KEYWORDS);
        boolean topGainer = containsAny(text, TOP_GAINER_KEYWORDS);

        List<StockProperties.Company> matched = matchCompanies(text);
        boolean companyMentioned = !matched.isEmpty();
        boolean stockIntent = strong || topGainer || (companyMentioned && weak);

        if (!stockIntent) {
            return StockQuery.none();
        }
        if ((targetList || topGainer) && matched.isEmpty()) {
            matched = new ArrayList<>(props.getCompanies());
        }
        boolean ambiguous = matched.isEmpty();
        return new StockQuery(true, ambiguous, topGainer, matched);
    }

    private List<StockProperties.Company> matchCompanies(String text) {
        Set<StockProperties.Company> result = new LinkedHashSet<>();
        for (StockProperties.Company company : props.getCompanies()) {
            if (matches(text, company.getName())
                    || matches(text, company.getSymbol())
                    || matches(text, company.getProviderSymbol())) {
                result.add(company);
                continue;
            }
            for (String alias : company.getAliases()) {
                if (matches(text, alias)) {
                    result.add(company);
                    break;
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static boolean matches(String text, String candidate) {
        String n = normalize(candidate);
        return !n.isBlank() && text.contains(n);
    }

    private static boolean containsAny(String text, List<String> keys) {
        for (String key : keys) {
            if (text.contains(normalize(key))) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，。！？?！、:：;；()（）\\[\\]【】._\\-/]+", "")
                .trim();
    }
}
