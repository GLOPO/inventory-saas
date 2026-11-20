package com.glory.inventorysaas.service;

import com.glory.inventorysaas.dto.response.NotificationResponse;
import com.glory.inventorysaas.entity.enums.NotificationType;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    void sendNotification(UUID companyId, UUID recipientUserId, String title, String message, NotificationType type, UUID referenceId);

    List<NotificationResponse> getMyNotifications();

    long getUnreadCount();

    NotificationResponse markAsRead(UUID notificationId);

    int markAllAsRead();
}
