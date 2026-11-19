package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record StockSaleRequest (
        @NotNull
        UUID productId,
        @NotNull
        Integer quantity
) {
}
