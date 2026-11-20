package com.glory.inventorysaas.event;

import java.util.UUID;

public record CustomerOrderAssignedEvent(
        UUID companyId,
        UUID orderId,
        UUID warehouseId,
        String warehouseName,
        UUID previousWarehouseId,
        String previousWarehouseName,
        String customerEmail,
        boolean reassigned
) {}
