package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStaffRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email")
        String email
) {
}
