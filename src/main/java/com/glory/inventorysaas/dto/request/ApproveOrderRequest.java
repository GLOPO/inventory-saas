package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApproveOrderRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,
        UUID warehouseId
) {
}
