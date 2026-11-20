package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordSetupRequest(
        @NotBlank(message = "Setup Token is required")
        String token,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must contain at least 8 characters")
        String password,

        @NotBlank(message = "Password confirmation is required")
        String confirmPassword

) {
}
