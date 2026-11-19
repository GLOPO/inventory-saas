package com.glory.inventorysaas.dto.response;

import java.io.Serializable;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
