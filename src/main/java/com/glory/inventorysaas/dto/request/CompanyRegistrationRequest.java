package com.glory.inventorysaas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CompanyRegistrationRequest(
        @NotBlank
        String name,
        @NotBlank
        @Email
        String adminEmail,
        @NotBlank
        String password
) {
}
