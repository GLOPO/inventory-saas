package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StockSaleRequest (
        @NotNull
        UUID productId,
        @NotNull
        UUID sourceWarehouseId,
        @NotNull
        Integer quantity
) {
}
