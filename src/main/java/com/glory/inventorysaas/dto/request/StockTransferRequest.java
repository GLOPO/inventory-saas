package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record StockTransferRequest(
        @NotNull
        UUID productId,
        @NotNull
        UUID destinationWarehouseId,
        @NotNull
        Integer quantity
) {
}
