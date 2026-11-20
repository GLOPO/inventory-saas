package com.glory.inventorysaas.event;

import java.util.UUID;

public record CustomerOrderPlacedEvent(
        UUID companyId,
        UUID orderId,
        String customerEmail,
        Integer quantity,
        String deliveryLocation,
        String requestedProductName,
        UUID selectedProductId
) {
}
