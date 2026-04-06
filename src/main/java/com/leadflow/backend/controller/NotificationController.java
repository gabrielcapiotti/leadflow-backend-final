package com.leadflow.backend.controller;

import com.leadflow.backend.dto.NotificationResponse;
import com.leadflow.backend.dto.SendNotificationRequest;
import com.leadflow.backend.service.notification.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Enviar notificação para um usuário
     *
     * @param request dados da notificação
     * @return resposta com dados da notificação enviada
     */
    @PostMapping("/send")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> send(
        @Valid @RequestBody SendNotificationRequest request,
        Authentication principal
    ) {
        try {
            String tenantId = extractTenantId(principal);
            NotificationResponse response = notificationService.send(request, tenantId, principal);
            return ResponseEntity.ok(response);
        } catch (IllegalAccessException e) {
            log.warn("Acesso negado ao enviar notificação: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * Listar notificações do usuário autenticado
     *
     * @param page número da página (0-indexed)
     * @param principal usuário autenticado
     * @return página de notificações
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> listMy(
        @RequestParam(defaultValue = "0") int page,
        Authentication principal
    ) {
        log.info("Listando notificações para usuário: {}", principal.getName());
        
        // Implementação simplificada - retorna página vazia por enquanto
        Page<NotificationResponse> emptyPage = new PageImpl<>(
            Collections.emptyList(),
            PageRequest.of(page, 20),
            0
        );
        
        return ResponseEntity.ok(emptyPage);
    }

    /**
     * Marcar notificação como lida
     *
     * @param notificationId ID da notificação
     * @param principal usuário autenticado
     * @return sem conteúdo
     */
    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(
        @PathVariable String notificationId,
        Authentication principal
    ) {
        try {
            String tenantId = extractTenantId(principal);
            notificationService.markAsRead(notificationId, tenantId);
            return ResponseEntity.noContent().build();
        } catch (IllegalAccessException e) {
            log.warn("Acesso negado ao marcar notificação como lida: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * Contar notificações não lidas
     *
     * @param principal usuário autenticado
     * @return quantidade de não lidas
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
        Authentication principal
    ) {
        String tenantId = extractTenantId(principal);
        java.util.UUID userId = java.util.UUID.fromString(principal.getName());
        long unreadCount = notificationService.countUnread(tenantId, userId);
        
        return ResponseEntity.ok(new UnreadCountResponse(unreadCount));
    }

    private String extractTenantId(Authentication principal) {
        return principal.getDetails() != null ? 
            principal.getDetails().toString() : 
            "00000000-0000-0000-0000-000000000000";
    }

    /**
     * DTO para resposta de contagem
     */
    static class UnreadCountResponse {
        public long unreadCount;

        public UnreadCountResponse(long unreadCount) {
            this.unreadCount = unreadCount;
        }
    }
}
