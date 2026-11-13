package com.glory.inventorysaas.dto.request;

import com.glory.inventorysaas.entity.enums.MovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InventoryRequest(
        @NotNull
        UUID warehouseId,
        @NotNull
        UUID productId,
        @NotNull
        Integer quantity
) {
}
