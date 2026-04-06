package com.leadflow.backend.dto;

import com.leadflow.backend.entities.notification.NotificationStatus;
import com.leadflow.backend.entities.notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private UUID notificationId;

    private NotificationType type;

    private String template;

    private String subject;

    private String recipient;

    private NotificationStatus status;

    private Instant sentAt;

    private Instant readAt;

    private String errorMessage;
}
