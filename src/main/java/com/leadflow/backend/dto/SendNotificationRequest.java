package com.leadflow.backend.dto;

import com.leadflow.backend.entities.notification.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendNotificationRequest {

    /**
     * ID do destinatário. Se null, envia para o usuário autenticado.
     */
    private UUID recipientId;

    @NotNull(message = "Tipo de notificação é obrigatório")
    private NotificationType type;

    @NotBlank(message = "Template é obrigatório")
    private String template;

    /**
     * Obrigatório apenas para EMAIL
     */
    private String subject;

    /**
     * Variáveis para renderizar o template
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * Metadados adicionais
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
