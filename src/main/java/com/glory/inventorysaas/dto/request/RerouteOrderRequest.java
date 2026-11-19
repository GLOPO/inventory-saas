package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RerouteOrderRequest(
        @NotNull(message = "Target warehouse is required")
        UUID targetWarehouseId,

        @NotBlank(message = "Rerouting reason is required")
        @Size(max = 500)
        String reroutingReason
) {
}
