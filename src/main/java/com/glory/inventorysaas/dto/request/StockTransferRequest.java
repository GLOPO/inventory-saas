package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StockTransferRequest(
        @NotNull
        UUID productId,
        @NotNull
        UUID sourceWarehouseId,
        @NotNull
        UUID destinationWarehouseId,
        @NotNull
        Integer quantity
) {
}
