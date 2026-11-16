package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProductRequest(
        @NotBlank
        String name,
        @NotBlank
        String sku
) {
}
