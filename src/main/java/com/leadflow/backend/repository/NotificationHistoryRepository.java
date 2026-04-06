package com.leadflow.backend.repository;

import com.leadflow.backend.entities.notification.NotificationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {
    Page<NotificationHistory> findByTenantIdAndRecipientId(UUID tenantId, UUID recipientId, Pageable pageable);

    Page<NotificationHistory> findByTenantId(UUID tenantId, Pageable pageable);

    long countByTenantIdAndRecipientIdAndReadAtIsNull(UUID tenantId, UUID recipientId);
}
