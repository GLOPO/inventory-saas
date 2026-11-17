package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record PlaceOrderResponse(
        UUID id,
        String matchedProductName,
        String requestedProductName,
        Integer quantity,
        String customerEmail,
        String deliveryLocation,
        OrderStatus status,
        Instant createdAt
) {
}
