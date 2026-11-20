package com.glory.inventorysaas.event;

import java.util.UUID;

public record UserProvisionedEvent(
        UUID userId,
        UUID companyId,
        String email,
        String roleName,
        String companyName,
        String warehouseName
) {
}
