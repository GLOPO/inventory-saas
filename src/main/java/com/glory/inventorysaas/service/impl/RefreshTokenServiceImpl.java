package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.response.AuthResponse;
import com.glory.inventorysaas.entity.RefreshToken;
import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.mapper.UserMapper;
import com.glory.inventorysaas.repository.RefreshTokenRepository;
import com.glory.inventorysaas.security.CustomUserDetailsService;
import com.glory.inventorysaas.security.JwtService;
import com.glory.inventorysaas.security.UserPrincipal;
import com.glory.inventorysaas.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserMapper userMapper;

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    @Transactional
    public String issueRefreshToken(User user) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = RefreshToken.builder()
                .tokenHash(sha256(rawToken))
                .user(user)
                .expiresAt(Instant.now().plus(REFRESH_TTL))
                .revoked(false)
                .build();

        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Override
    @Transactional
    public AuthResponse rotateRefreshToken(String rawRefreshToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.isRevoked()) {
            // Replay detected: revoke all active refresh tokens for this user
            refreshTokenRepository.revokeAllByUserId(existing.getUser().getId());
            throw new BadCredentialsException("Refresh token has been revoked");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            existing.setRevoked(true);
            throw new BadCredentialsException("Refresh token has expired");
        }

        existing.setRevoked(true);

        User user = existing.getUser();
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(user.getEmail());

        String newAccessToken = jwtService.generateToken(principal);
        String newRefreshToken = issueRefreshToken(user);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                userMapper.toResponse(user)
        );
    }

    @Override
    @Transactional
    public void revokeForUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }
}
