package com.zwskchat.zwskchat.stock.provider;

import com.zwskchat.zwskchat.config.properties.StockProperties;
import com.zwskchat.zwskchat.stock.dto.StockQuote;

import java.util.Optional;

public interface StockQuoteProvider {
    String name();

    Optional<StockQuote> getQuote(StockProperties.Company company);
}
