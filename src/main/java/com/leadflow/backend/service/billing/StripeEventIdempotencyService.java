package com.leadflow.backend.service.billing;

import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.repository.StripeEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de idempotência para Stripe Webhooks.
 * 
 * Garante que eventos duplicados não sejam processados duas vezes.
 * Stripe pode enviar o mesmo evento várias vezes — este serviço previne
 * duplicação de cobrança, estado inconsistente e bugs impossíveis de rastrear.
 * 
 * Fluxo:
 * 1. Webhook recebido
 * 2. Verificar se eventId já foi visto (isDuplicate)
 * 3. Se SIM → retornar 200 OK (não processar novamente)
 * 4. Se NÃO → processar e salvar evento
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeEventIdempotencyService {

    private final StripeEventLogRepository eventLogRepository;

    /**
     * Verifica se um evento já foi recebido e processado.
     * 
     * @param eventId ID único do evento Stripe (e.g., "evt_1234567890")
     * @return true se evento já existe, false caso contrário
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(String eventId) {
        boolean exists = eventLogRepository.findByEventId(eventId).isPresent();
        if (exists) {
            log.info("Evento duplicado detectado: {}", eventId);
        }
        return exists;
    }

    /**
     * Salva um novo evento recebido do webhook Stripe.
     * 
     * @param eventId ID único do evento (validar que é único antes de chamar)
     * @param eventType Tipo do evento (e.g., "charge.succeeded", "invoice.paid")
     * @param payload JSON payload completo do evento
     * @param tenantId ID do tenant (opcional, pode ser null se público)
     * @param customerId ID do customer Stripe (opcional)
     * @return StripeEventLog salvo
     */
    @Transactional
    public StripeEventLog saveEvent(
            String eventId,
            String eventType,
            String payload,
            UUID tenantId,
            String customerId
    ) {
        StripeEventLog event = StripeEventLog.builder()
                .eventId(eventId)
                .eventType(eventType)
                .payload(payload)
                .tenantId(tenantId)
                .customerId(customerId)
                .status(StripeEventLog.EventProcessingStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .build();

        StripeEventLog saved = eventLogRepository.save(event);
        log.info("Evento salvo para processamento: {} (tipo: {})", eventId, eventType);
        return saved;
    }

    /**
     * Marca um evento como processado com sucesso.
     * 
     * @param eventId ID do evento
     */
    @Transactional
    public void markProcessed(String eventId) {
        Optional<StripeEventLog> eventOpt = eventLogRepository.findByEventId(eventId);
        
        if (eventOpt.isPresent()) {
            StripeEventLog event = eventOpt.get();
            event.setStatus(StripeEventLog.EventProcessingStatus.SUCCESS);
            event.setProcessedAt(LocalDateTime.now());
            eventLogRepository.save(event);
            log.info("Evento marcado como processado: {}", eventId);
        } else {
            log.warn("Tentativa de marcar evento inexistente como processado: {}", eventId);
        }
    }

    /**
     * Marca um evento como falhado e agenda retry.
     * 
     * @param eventId ID do evento
     * @param errorMessage Mensagem de erro
     */
    @Transactional
    public void markFailed(String eventId, String errorMessage) {
        Optional<StripeEventLog> eventOpt = eventLogRepository.findByEventId(eventId);
        
        if (eventOpt.isPresent()) {
            StripeEventLog event = eventOpt.get();
            event.setLastError(errorMessage);
            event.setRetryCount(event.getRetryCount() + 1);

            if (event.getRetryCount() >= event.getMaxRetries()) {
                event.setStatus(StripeEventLog.EventProcessingStatus.FAILED);
                log.error("Evento atingiu limite de retries: {} (erro: {})", eventId, errorMessage);
            } else {
                event.setStatus(StripeEventLog.EventProcessingStatus.RETRY_PENDING);
                // Exponential backoff: 2^retryCount segundos (min, max 300s)
                long delay = Math.min((long) Math.pow(2, event.getRetryCount()), 300);
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
                log.warn("Evento agendado para retry em {}s: {} (tentativa {}/{})", 
                    delay, eventId, event.getRetryCount(), event.getMaxRetries());
            }

            eventLogRepository.save(event);
        }
    }

    /**
     * Obtém informações de um evento pelo ID.
     * 
     * @param eventId ID do evento
     * @return Optional com o evento se encontrado
     */
    @Transactional(readOnly = true)
    public Optional<StripeEventLog> getEvent(String eventId) {
        return eventLogRepository.findByEventId(eventId);
    }
}
