package com.glory.inventorysaas.dto.request;

import com.glory.inventorysaas.entity.Company;

public record WarehouseRequest(
        String name,
        String code
) {
}
