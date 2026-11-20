package com.glory.inventorysaas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "password_setup_token")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordSetUpToken extends BaseEntity{
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;
}
