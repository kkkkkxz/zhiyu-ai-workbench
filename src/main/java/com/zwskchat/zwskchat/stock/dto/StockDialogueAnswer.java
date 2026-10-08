package com.zwskchat.zwskchat.stock.dto;

import java.util.List;

public record StockDialogueAnswer(String text, List<StockQuote> quotes) {
}
