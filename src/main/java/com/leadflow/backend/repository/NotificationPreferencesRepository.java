package com.leadflow.backend.repository;

import com.leadflow.backend.entities.notification.NotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {
    Optional<NotificationPreferences> findByUserId(UUID userId);

    Optional<NotificationPreferences> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
