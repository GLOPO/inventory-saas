package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.request.CompletePasswordSetupRequest;

import java.util.UUID;

public interface PasswordSetupLinkService {
    String generateSetupLink(UUID userId);

    void completePasswordSetUp(CompletePasswordSetupRequest request);
}
