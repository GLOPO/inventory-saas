package com.glory.inventorysaas.dto.request;

import com.glory.inventorysaas.entity.Company;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record WarehouseRequest(
        @NotBlank(message = "Ware house name is required")
        String name,

        @NotBlank(message = "A code is required")
        String code,

        @NotBlank(message = "An address is required")
        String address
) {
}
