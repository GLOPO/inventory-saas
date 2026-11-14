package com.glory.inventorysaas.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtils {
    public UUID getCurrentCompanyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {

            throw new IllegalStateException("User is not authenticated");
        }

        return userPrincipal.getCompanyId();
    }

    public String getCurrentCompanyCacheKey() {
        return getCurrentCompanyId().toString();
    }}
