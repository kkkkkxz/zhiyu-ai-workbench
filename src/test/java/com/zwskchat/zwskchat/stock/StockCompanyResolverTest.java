package com.zwskchat.zwskchat.stock;

import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockQuery;
import com.zwskchat.zwskchat.stock.service.StockCompanyResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockCompanyResolverTest {

    private final StockProperties props = new StockProperties();
    private final StockCompanyResolver resolver = new StockCompanyResolver(props);

    @Test
    void resolvesCompanyAliasWithWeakRealtimeWording() {
        StockQuery query = resolver.resolve("苹果现在多少钱");

        assertThat(query.stockIntent()).isTrue();
        assertThat(query.companies()).extracting(StockProperties.Company::getSymbol).containsExactly("AAPL");
    }

    @Test
    void resolvesTargetCompanyRankingToConfiguredCompanies() {
        StockQuery query = resolver.resolve("目标公司今天谁涨得最多");

        assertThat(query.stockIntent()).isTrue();
        assertThat(query.rankTopGainer()).isTrue();
        assertThat(query.companies()).hasSize(props.getCompanies().size());
    }

    @Test
    void ignoresGeneralCompanyQuestionWithoutMarketWording() {
        StockQuery query = resolver.resolve("苹果公司创始人是谁");

        assertThat(query.stockIntent()).isFalse();
    }
}
