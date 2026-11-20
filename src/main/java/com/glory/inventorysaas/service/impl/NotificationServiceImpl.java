package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.response.NotificationResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.Notification;
import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.entity.enums.NotificationType;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.NotificationMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.NotificationRepository;
import com.glory.inventorysaas.repository.UserRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(UUID companyId, UUID recipientUserId, String title, String message, NotificationType type, UUID referenceId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company Not Found"));

        User recipient = userRepository.getReferenceById(recipientUserId);

        Notification notification = Notification.builder()
                .company(company)
                .isRead(false)
                .message(message)
                .title(title)
                .type(type)
                .recipient(recipient)
                .referenceId(referenceId)
                .build();

        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        UUID userId = securityUtils.getCurrentUserId();
        UUID companyId = securityUtils.getCurrentCompanyId();

        List<Notification> notifications = notificationRepository.findByRecipientIdAndCompanyIdOrderByCreatedAtDesc(userId, companyId, PageRequest.of(0, 30));

        return notifications.stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        UUID userId = securityUtils.getCurrentUserId();
        UUID companyId = securityUtils.getCurrentCompanyId();

        return notificationRepository.countByRecipientIdAndCompanyIdAndIsReadFalse(userId, companyId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        UUID userId = securityUtils.getCurrentUserId();
        UUID companyId = securityUtils.getCurrentCompanyId();

        Notification notification = notificationRepository.findByIdAndRecipientIdAndCompanyId(notificationId, userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification Not Found."));

        if(!notification.isRead()) {
            notification.setRead(true);
        }
        notificationRepository.save(notification);

        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead() {
        UUID userId = securityUtils.getCurrentUserId();
        UUID companyId = securityUtils.getCurrentCompanyId();

        return notificationRepository.markAllAsRead(userId, companyId);
    }
}
