package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.PasswordSetUpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetUpToken, UUID> {

    Optional<PasswordSetUpToken> findByTokenHash(String tokenHash);

    Optional<PasswordSetUpToken> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
