package com.glory.inventorysaas.event;

import java.util.UUID;

public record StockTransferResolvedEvent(
        UUID companyId,
        UUID transferRequestId,
        UUID requestingWarehouseId,
        String productName,
        String sourceWarehouseName,
        int quantity,
        boolean accepted
) {
}
