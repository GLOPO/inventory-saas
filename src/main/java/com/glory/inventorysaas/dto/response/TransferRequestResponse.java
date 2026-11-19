package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.enums.TransferStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record TransferRequestResponse(
        UUID id,
        String productName,
        String sourceWarehouseName,
        String destinationWarehouseName,
        Integer quantity,
        TransferStatus status,
        UUID orderId,
        Instant createdAt
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
