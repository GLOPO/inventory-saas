package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateManagerRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotNull
        UUID warehouseId
) {
}
