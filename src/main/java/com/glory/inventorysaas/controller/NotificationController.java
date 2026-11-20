package com.glory.inventorysaas.controller;

import com.glory.inventorysaas.dto.response.NotificationResponse;
import com.glory.inventorysaas.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> myNotifications() {
        return ResponseEntity.ok(
                notificationService.getMyNotifications()
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unread() {
        return ResponseEntity.ok(
                Map.of("unreadCount", notificationService.getUnreadCount())
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(
                notificationService.markAsRead(notificationId)
        );
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        return ResponseEntity.ok(
                Map.of("updated", notificationService.markAllAsRead())
        );
    }
}
