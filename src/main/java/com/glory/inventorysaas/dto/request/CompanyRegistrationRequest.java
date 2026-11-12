package com.glory.inventorysaas.dto.request;

public record CompanyRegistrationRequest(
        String name,
        String adminEmail,
        String password
) {
}
