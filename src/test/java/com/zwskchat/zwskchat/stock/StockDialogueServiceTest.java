package com.zwskchat.zwskchat.stock;

import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockDialogueAnswer;
import com.zwskchat.zwskchat.stock.dto.StockQuote;
import com.zwskchat.zwskchat.stock.provider.StockQuoteProvider;
import com.zwskchat.zwskchat.stock.provider.impl.MockStockQuoteProvider;
import com.zwskchat.zwskchat.stock.service.StockCompanyResolver;
import com.zwskchat.zwskchat.stock.service.StockDialogueService;
import com.zwskchat.zwskchat.stock.service.StockQuoteService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StockDialogueServiceTest {

    @Test
    void answersSingleCompanyStockQuestionWithMockNotice() {
        StockProperties props = new StockProperties();
        props.setProvider("mock");
        props.setApiKey("");
        StockCompanyResolver resolver = new StockCompanyResolver(props);
        StockQuoteService quoteService = new StockQuoteService(props, List.of(new MockStockQuoteProvider()));
        StockDialogueService service = new StockDialogueService(props, resolver, quoteService);

        Optional<StockDialogueAnswer> answer = service.tryAnswer("英伟达现在股价多少", false);

        assertThat(answer).isPresent();
        assertThat(answer.get().text()).contains("NVIDIA").contains("示例行情").contains("不构成投资建议");
    }

    @Test
    void autoModeUsesChinaProviderBeforeMockWithoutApiKey() {
        StockProperties props = new StockProperties();
        props.setProvider("auto");
        props.setApiKey("");
        StockCompanyResolver resolver = new StockCompanyResolver(props);
        StockQuoteService quoteService = new StockQuoteService(props, List.of(
                new FixedProvider("tencent-finance"),
                new MockStockQuoteProvider()
        ));
        StockDialogueService service = new StockDialogueService(props, resolver, quoteService);

        Optional<StockDialogueAnswer> answer = service.tryAnswer("腾讯现在股价多少", false);

        assertThat(answer).isPresent();
        assertThat(answer.get().text())
                .contains("Tencent Holdings")
                .contains("腾讯财经")
                .doesNotContain("示例行情");
    }

    private record FixedProvider(String name) implements StockQuoteProvider {
        @Override
        public Optional<StockQuote> getQuote(StockProperties.Company company) {
            return Optional.of(new StockQuote(
                    company.getName(),
                    company.getSymbol(),
                    company.getMarket(),
                    company.getCurrency(),
                    new BigDecimal("458.00"),
                    new BigDecimal("0.80"),
                    new BigDecimal("0.17"),
                    9284548L,
                    "2026/05/13 11:15:23",
                    Instant.now(),
                    "腾讯财经",
                    true,
                    false
            ));
        }
    }
}
