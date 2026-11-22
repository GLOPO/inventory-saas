package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.response.AuthResponse;
import com.glory.inventorysaas.entity.User;

import java.util.UUID;

public interface RefreshTokenService {
    String issueRefreshToken(User user);

    AuthResponse rotateRefreshToken (String rawRefreshToken);

    void revokeForUser (UUID userId);
}
