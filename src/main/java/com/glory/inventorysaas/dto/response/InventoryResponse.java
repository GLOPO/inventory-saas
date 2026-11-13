package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.enums.MovementType;

import java.util.UUID;

public record InventoryResponse(
        UUID id,
        UUID productId,
        UUID warehouseId,
        Integer quantity
) {
}
