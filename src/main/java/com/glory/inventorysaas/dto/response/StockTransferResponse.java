package com.glory.inventorysaas.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StockTransferResponse(
        UUID id,
        UUID productId,
        String sourceWarehouseName,
        String destinationWarehouseName,
        Integer quantity,
        Instant createdAt
) {
}
