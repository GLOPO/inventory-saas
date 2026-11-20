package com.glory.inventorysaas.dto.response;

import com.glory.inventorysaas.entity.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String message,
        NotificationType type,
        UUID referenceId,
        boolean isRead,
        Instant createdAt
) {
}
