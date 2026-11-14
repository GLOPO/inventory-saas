package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StockPurchaseRequest(
        @NotNull
        UUID productId,
        @NotNull
        UUID destinationWarehouseId,
        @NotNull
        Integer quantity
) {
}
