package com.leadflow.backend.service.notification;

import com.leadflow.backend.dto.NotificationResponse;
import com.leadflow.backend.dto.SendNotificationRequest;
import com.leadflow.backend.entities.notification.NotificationHistory;
import com.leadflow.backend.entities.notification.NotificationPreferences;
import com.leadflow.backend.entities.notification.NotificationStatus;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.repository.NotificationHistoryRepository;
import com.leadflow.backend.repository.NotificationPreferencesRepository;
import com.leadflow.backend.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationHistoryRepository historyRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final SendGridEmailService emailService;
    private final TemplateEngine templateEngine;

    @Transactional
    public NotificationResponse send(SendNotificationRequest request, String tenantId, Authentication principal) throws IllegalAccessException {
        try {
            // 1. Resolver destinatário
            UUID recipientId = request.getRecipientId();
            UUID currentUserId = UUID.fromString(principal.getName());

            if (recipientId == null) {
                recipientId = currentUserId;
            }

            final UUID finalRecipientId = recipientId;

            // 2. Validar permissões (só pode enviar para si mesmo, ou ser admin)
            if (!finalRecipientId.equals(currentUserId)) {
                boolean isAdmin = principal.getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_ADMIN"));

                if (!isAdmin) {
                    throw new IllegalAccessException("Não pode enviar notificações para outros usuários");
                }
            }

            // 3. Buscar usuário destinatário
            User recipient = userRepository.findById(finalRecipientId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário destinatário não encontrado"));

            // 4. Verificar preferências
            NotificationPreferences prefs = preferencesRepository.findByUserId(finalRecipientId)
                .orElseGet(() -> createDefaultPreferences(finalRecipientId, UUID.fromString(tenantId)));

            boolean shouldSend = switch (request.getType()) {
                case EMAIL -> prefs.getEmailEnabled();
                case SMS -> prefs.getSmsEnabled();
                case PUSH -> prefs.getPushEnabled();
            };

            if (!shouldSend) {
                log.info("Notificação não enviada - tipo desativado para usuário: {}", finalRecipientId);
                return buildResponse(null, request, recipient.getEmail(), "SKIPPED");
            }

            // 5. Renderizar template
            String message = renderTemplate(request.getTemplate(), request.getVariables());

            // 6. Enviar conforme tipo
            try {
                switch (request.getType()) {
                    case EMAIL -> {
                        if (request.getSubject() == null || request.getSubject().isBlank()) {
                            throw new IllegalArgumentException("Subject é obrigatório para EMAIL");
                        }
                        emailService.sendEmail(recipient.getEmail(), request.getSubject(), message);
                    }
                    case SMS -> log.warn("SMS não implementado ainda");
                    case PUSH -> log.warn("PUSH não implementado ainda");
                }
            } catch (Exception e) {
                log.error("Erro ao enviar notificação", e);
                return saveFailed(tenantId, finalRecipientId, request, recipient.getEmail(), e.getMessage());
            }

            // 7. Log no histórico
            NotificationHistory history = NotificationHistory.builder()
                .tenantId(UUID.fromString(tenantId))
                .recipientId(finalRecipientId)
                .type(request.getType())
                .template(request.getTemplate())
                .subject(request.getSubject())
                .message(message)
                .status(NotificationStatus.SENT)
                .sentAt(Instant.now())
                .build();

            historyRepository.save(history);

            return buildResponse(history, request, recipient.getEmail(), "SENT");

        } catch (IllegalAccessException e) {
            log.warn("Erro de acesso ao enviar notificação: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Erro na tentativa de enviar notificação", e);
            throw new RuntimeException("Erro ao enviar notificação: " + e.getMessage());
        }
    }

    @Transactional
    public void markAsRead(String notificationId, String tenantId) throws IllegalAccessException {
        UUID notifId = UUID.fromString(notificationId);
        NotificationHistory history = historyRepository.findById(notifId)
            .orElseThrow(() -> new IllegalArgumentException("Notificação não encontrada"));

        if (!history.getTenantId().toString().equals(tenantId)) {
            throw new IllegalAccessException("Acesso negado");
        }

        history.setReadAt(Instant.now());
        historyRepository.save(history);
    }

    public long countUnread(String tenantId, UUID userId) {
        return historyRepository.countByTenantIdAndRecipientIdAndReadAtIsNull(
            UUID.fromString(tenantId),
            userId
        );
    }

    private NotificationPreferences createDefaultPreferences(UUID userId, UUID tenantId) {
        NotificationPreferences prefs = NotificationPreferences.builder()
            .tenantId(tenantId)
            .userId(userId)
            .emailEnabled(true)
            .smsEnabled(false)
            .pushEnabled(false)
            .alertFrequency("realtime")
            .build();

        return preferencesRepository.save(prefs);
    }

    private String renderTemplate(String templateName, java.util.Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        
        try {
            return templateEngine.process("notifications/" + templateName, context);
        } catch (Exception e) {
            log.warn("Template {} não encontrado, usando mensagem genérica", templateName);
            return variables.getOrDefault("message", "").toString();
        }
    }

    private NotificationResponse buildResponse(NotificationHistory history, SendNotificationRequest request,
                                                String recipient, String status) {
        return NotificationResponse.builder()
            .notificationId(history != null ? history.getId() : UUID.randomUUID())
            .type(request.getType())
            .template(request.getTemplate())
            .subject(request.getSubject())
            .recipient(recipient)
            .status(NotificationStatus.valueOf(status))
            .sentAt(Instant.now())
            .build();
    }

    private NotificationResponse saveFailed(String tenantId, UUID recipientId, SendNotificationRequest request,
                                            String recipient, String errorMessage) {
        NotificationHistory history = NotificationHistory.builder()
            .tenantId(UUID.fromString(tenantId))
            .recipientId(recipientId)
            .type(request.getType())
            .template(request.getTemplate())
            .subject(request.getSubject())
            .status(NotificationStatus.FAILED)
            .sentAt(Instant.now())
            .errorMessage(errorMessage)
            .build();

        historyRepository.save(history);

        return NotificationResponse.builder()
            .notificationId(history.getId())
            .type(request.getType())
            .template(request.getTemplate())
            .subject(request.getSubject())
            .recipient(recipient)
            .status(NotificationStatus.FAILED)
            .sentAt(Instant.now())
            .errorMessage(errorMessage)
            .build();
    }
}
