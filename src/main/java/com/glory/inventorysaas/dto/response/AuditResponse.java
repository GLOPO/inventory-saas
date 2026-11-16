package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.enums.MovementType;

import java.io.Serializable;
import java.util.UUID;

public record AuditResponse(
        UUID id,
        String productName,
        String sourceWarehouseName,
        String destinationWarehouseName,
        Integer quantity,
        MovementType movementType
) implements Serializable {

    private static final long serialVersionUID = 1L;
}