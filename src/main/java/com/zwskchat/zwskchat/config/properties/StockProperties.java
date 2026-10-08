package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.stock")
public class StockProperties {

    private boolean enabled = true;
    /** auto / mock / twelve-data */
    private String provider = "auto";
    private String baseUrl = "https://api.twelvedata.com";
    private String apiKey;
    /** Set true when your data subscription returns delayed quotes. */
    private boolean delayed = false;
    private int requestTimeoutSeconds = 5;
    private int cacheTtlSeconds = 20;
    private int maxCompaniesPerAnswer = 5;
    private List<Company> companies = defaultCompanies();

    @Data
    public static class Company {
        private String name;
        private String symbol;
        private String providerSymbol;
        private String market;
        private String currency;
        private List<String> aliases = new ArrayList<>();
    }

    private static List<Company> defaultCompanies() {
        return new ArrayList<>(List.of(
                company("Apple", "AAPL", "AAPL", "NASDAQ", "USD", "apple", "苹果", "苹果公司"),
                company("NVIDIA", "NVDA", "NVDA", "NASDAQ", "USD", "nvidia", "英伟达", "英伟达公司"),
                company("Microsoft", "MSFT", "MSFT", "NASDAQ", "USD", "microsoft", "微软", "微软公司"),
                company("Tencent Holdings", "00700.HK", "0700.HK", "HKEX", "HKD", "tencent", "腾讯", "腾讯控股"),
                company("Alibaba", "BABA", "BABA", "NYSE", "USD", "alibaba", "阿里", "阿里巴巴")
        ));
    }

    private static Company company(String name, String symbol, String providerSymbol,
                                   String market, String currency, String... aliases) {
        Company c = new Company();
        c.setName(name);
        c.setSymbol(symbol);
        c.setProviderSymbol(providerSymbol);
        c.setMarket(market);
        c.setCurrency(currency);
        c.setAliases(new ArrayList<>(List.of(aliases)));
        return c;
    }
}
