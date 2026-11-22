package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        String name,

        @NotBlank(message = "Product SKU is required")
        String sku
) {}
