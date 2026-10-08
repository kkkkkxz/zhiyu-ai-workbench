package com.zwskchat.zwskchat.stock.dto;

import com.zwskchat.zwskchat.config.properties.StockProperties;

import java.util.List;

public record StockQuery(
        boolean stockIntent,
        boolean ambiguous,
        boolean rankTopGainer,
        List<StockProperties.Company> companies
) {
    public static StockQuery none() {
        return new StockQuery(false, false, false, List.of());
    }
}
