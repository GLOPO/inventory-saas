package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferStockRequest(
        @NotNull(message = "Source warehouse is required")
        UUID sourceWarehouseId,

        @NotNull(message = "Product is required")
        UUID productId,

        @NotNull(message = "Quantity is required")
        @Min(1)
        Integer quantity,

        UUID orderId
) {
}
