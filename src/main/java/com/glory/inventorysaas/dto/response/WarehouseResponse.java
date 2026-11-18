package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.Company;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String name,
        String code,
        String companyName,
        Instant createdAt,
        String address,
        Double latitude,
        Double longitude
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
