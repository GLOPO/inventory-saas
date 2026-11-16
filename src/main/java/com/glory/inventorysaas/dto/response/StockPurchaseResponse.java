package com.glory.inventorysaas.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StockPurchaseResponse(
        UUID id,
        String productName,
        String destinationWarehouseName,
        Integer quantity,
        Instant createdAt
) {
}
