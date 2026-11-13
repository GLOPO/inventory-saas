package com.glory.inventorysaas.dto.response;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String sku,
        UUID companyId,
        Instant createdAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
