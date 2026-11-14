package com.glory.inventorysaas.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StockSaleResponse(
        UUID id,
        String productName,
        String sourceWarehouseName,
        Integer quantity,
        Instant createdAt
) {
}
