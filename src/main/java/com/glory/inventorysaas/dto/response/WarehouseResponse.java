package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.Company;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String name,
        String code,
        UUID companyId,
        Instant createdAt
) {
}
