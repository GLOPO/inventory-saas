package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.enums.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Role role,
        Company company
) {
}
