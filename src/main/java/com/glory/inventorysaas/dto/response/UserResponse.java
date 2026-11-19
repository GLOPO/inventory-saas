package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.enums.Role;

import java.io.Serializable;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Role role,
        CompanyResponse company
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
