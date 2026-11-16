package com.glory.inventorysaas.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UserResponse user
) {
}
