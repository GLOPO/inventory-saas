package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdAndCompanyIdOrderByCreatedAtDesc(UUID recipientId, UUID companyId, Pageable pageable);

    long countByRecipientIdAndCompanyIdAndIsReadFalse(UUID recipientId, UUID companyId);

    Optional<Notification> findByIdAndRecipientIdAndCompanyId(UUID id, UUID recipientId, UUID companyId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isRead = true
            WHERE n.recipient.id = :recipientId
            AND n.company.id = :companyId
            AND n.isRead = false
            """)
    int markAllAsRead(
            @Param("recipientId") UUID recipientId,
            @Param("companyId") UUID companyId
    );


}
