package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.CompletePasswordSetupRequest;
import com.glory.inventorysaas.entity.PasswordSetUpToken;
import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.repository.PasswordSetupTokenRepository;
import com.glory.inventorysaas.repository.UserRepository;
import com.glory.inventorysaas.service.PasswordSetupLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordSetupLinkServiceImpl implements PasswordSetupLinkService {
    private final PasswordSetupTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public String generateSetupLink(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String tokenHash = hashToken(rawToken);

        tokenRepository.deleteByUserId(userId);

        PasswordSetUpToken setupToken = PasswordSetUpToken.builder()
                .expiresAt(Instant.now().plusSeconds(1800))
                .used(false)
                .user(user)
                .tokenHash(tokenHash)
                .build();

        tokenRepository.save(setupToken);

        return frontendUrl.replaceAll("/+$", "")
                + "/setup-password?token=" + rawToken;
    }

    @Override
    @Transactional
    public void completePasswordSetUp(CompletePasswordSetupRequest request) {
        if(!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords must match"
            );
        }

        String tokenHash = hashToken(request.token());

        PasswordSetUpToken setUpToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or expired token"
                ));

        if (setUpToken.isUsed() || !setUpToken.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid or expired setup link"
            );
        }

        User user = setUpToken.getUser();
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setMustChangePassword(false);

        userRepository.save(user);

        setUpToken.setUsed(true);
        tokenRepository.save(setUpToken);

    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException(
                    "SHA_256 algorithm is unavailable", ex);
        }
    }
}
