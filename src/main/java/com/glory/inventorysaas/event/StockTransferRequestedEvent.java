package com.glory.inventorysaas.event;

import java.util.UUID;

public record StockTransferRequestedEvent(
        UUID companyId,
        UUID transferRequestId,
        UUID sourceWarehouseId,
        String requestingWarehouseName,
        String productName,
        int quantity
) {
}
