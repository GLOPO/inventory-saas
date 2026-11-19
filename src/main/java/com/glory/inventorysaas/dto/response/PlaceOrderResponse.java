package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.enums.OrderStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record PlaceOrderResponse(
        UUID id,
        String matchedProductName,
        String requestedProductName,
        Integer quantity,
        String customerEmail,
        String deliveryLocation,
        Double deliveryLatitude,
        Double deliveryLongitude,
        OrderStatus status,
        Instant createdAt,
        String warehouseName
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
