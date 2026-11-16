package com.glory.inventorysaas.dto.response;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name
) {}