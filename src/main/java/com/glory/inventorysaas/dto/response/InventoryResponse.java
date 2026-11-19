package com.glory.inventorysaas.dto.response;

import java.io.Serializable;
import java.util.UUID;

public record InventoryResponse(
        UUID id,
        String productName,
        String warehouseName,
        Integer quantity
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
