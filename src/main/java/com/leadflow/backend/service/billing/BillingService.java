package com.leadflow.backend.service.billing;

import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.util.StripeSubscriptionStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Serviço de billing baseado em eventos Stripe.
 * 
 * Responsabilidades:
 * - Atualizar status de subscription baseado em eventos Stripe
 * - Manter sincronização entre Stripe e banco de dados local
 * - Garantir idempotência (múltiplas chamadas = mesmo resultado)
 * - Suportar multi-tenancy
 * 
 * Fluxo esperado:
 * Webhook Stripe → StripeEventIdempotencyService → StripeWebhookProcessor → Handler → BillingService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Processa evento: invoice.paid
     * 
     * Atualiza subscription:
     * - status = ACTIVE
     * - lastPaymentDate = agora
     * - currentPeriodEnd = fim do período de faturamento
     * 
     * @param stripeCustomerId ID do customer Stripe
     * @param periodEndEpoch   Timestamp (segundos) do fim do período de faturamento
     */
    @Transactional
    public void handleInvoicePaid(String stripeCustomerId, long periodEndEpoch) {
        log.info("[BILLING] Processing invoice.paid: customerId={}, periodEnd={}", 
                stripeCustomerId, periodEndEpoch);

        var subscriptionOpt = subscriptionRepository
                .findByStripeCustomerId(stripeCustomerId);
        
        if (subscriptionOpt.isEmpty()) {
            log.warn("[BILLING] Subscription not ready yet for customer: {}. Will retry on next event.", stripeCustomerId);
            return;
        }
        
        Subscription subscription = subscriptionOpt.get();

        // Convertendo epoch (segundos) para LocalDateTime
        Instant periodEndInstant = Instant.ofEpochSecond(periodEndEpoch);
        LocalDateTime periodEndLocal = LocalDateTime.ofInstant(periodEndInstant, ZoneId.systemDefault());

        // Atualizar subscription
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setLastPaymentDate(LocalDateTime.now());
        subscription.setExpiresAt(periodEndLocal); // Usar expiresAt para armazenar fim do período

        saveWithStatusValidation(subscription);

        log.info("[BILLING] ✅ Invoice paid processed: customerId={}, status=ACTIVE, expiresAt={}", 
                stripeCustomerId, periodEndLocal);
    }

    /**
     * Processa evento: invoice.payment_failed
     * 
     * Atualiza subscription:
     * - status = PAST_DUE
     * 
     * @param stripeCustomerId ID do customer Stripe
     */
    @Transactional
    public void handleInvoicePaymentFailed(String stripeCustomerId) {
        log.info("[BILLING] Processing invoice.payment_failed: customerId={}", stripeCustomerId);

        var subscriptionOpt = subscriptionRepository
                .findByStripeCustomerId(stripeCustomerId);
        
        if (subscriptionOpt.isEmpty()) {
            log.warn("[BILLING] Subscription not ready yet for customer: {}. Will retry on next event.", stripeCustomerId);
            return;
        }
        
        Subscription subscription = subscriptionOpt.get();

        Subscription.SubscriptionStatus previousStatus = subscription.getStatus();
        subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);

        saveWithStatusValidation(subscription);

        log.info("[BILLING] ✅ Payment failure processed: customerId={}, status={} → PAST_DUE", 
                stripeCustomerId, previousStatus);
    }

    /**
     * Processa evento: customer.subscription.deleted
     * 
     * Atualiza subscription:
     * - status = CANCELLED
     * - cancelledAt = agora
     * 
     * @param stripeCustomerId ID do customer Stripe
     */
    @Transactional
    public void handleSubscriptionCancelled(String stripeCustomerId) {
        log.info("[BILLING] Processing customer.subscription.deleted: customerId={}", stripeCustomerId);

        var subscriptionOpt = subscriptionRepository
                .findByStripeCustomerId(stripeCustomerId);
        
        if (subscriptionOpt.isEmpty()) {
            log.warn("[BILLING] Subscription not found for customer: {}. Cannot cancel non-existent subscription.", stripeCustomerId);
            return;
        }
        
        Subscription subscription = subscriptionOpt.get();

        Subscription.SubscriptionStatus previousStatus = subscription.getStatus();
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());

        saveWithStatusValidation(subscription);

        log.info("[BILLING] ✅ Subscription cancelled: customerId={}, status={} → CANCELLED", 
                stripeCustomerId, previousStatus);
    }

    /**
     * Processa evento: checkout.session.completed
     * 
     * Vincula Stripe customer ID à subscription local (lazy population).
     * Chamado quando um checkout é concluído e o customer é criado.
     * 
     * @param stripeCustomerId ID do customer Stripe
     * @param stripeSubscriptionId ID da subscription Stripe
     */
    @Transactional
    public void handleCheckoutCompleted(String stripeCustomerId, String stripeSubscriptionId) {
        log.info("[BILLING] Processing checkout.session.completed: customerId={}, subscriptionId={}", 
                stripeCustomerId, stripeSubscriptionId);

        var subscriptionOpt = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId);
        
        if (subscriptionOpt.isEmpty()) {
            log.warn("[BILLING] Subscription not found for Stripe subscription: {}. Must be created first.", stripeSubscriptionId);
            return;
        }
        
        Subscription subscription = subscriptionOpt.get();

        // Atualizar customer ID (lazy population)
        subscription.setStripeCustomerId(stripeCustomerId);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(LocalDateTime.now());

        saveWithStatusValidation(subscription);

        log.info("[BILLING] ✅ Checkout completed: subscriptionId={}, customerId linked", 
                stripeSubscriptionId);
    }

    /**
     * Busca subscription por customer Stripe ID.
     * Útil para verificar se subscription existe antes de processar evento.
     * 
     * @param stripeCustomerId ID do customer Stripe
     * @return true se subscription existe
     */
    public boolean subscriptionExists(String stripeCustomerId) {
        return subscriptionRepository.findByStripeCustomerId(stripeCustomerId).isPresent();
    }

    /**
     * Obtém status atual de uma subscription por customer Stripe ID.
     * 
     * @param stripeCustomerId ID do customer Stripe
     * @return Status da subscription, ou null se não encontrada
     */
    public Subscription.SubscriptionStatus getCurrentStatus(String stripeCustomerId) {
        return subscriptionRepository
                .findByStripeCustomerId(stripeCustomerId)
                .map(Subscription::getStatus)
                .orElse(null);
    }

    /**
     * Helper: Salvar com validação de status (segurança).
     * Garante que status nunca é null antes de persistir.
     * 
     * @param subscription Subscription a ser salva
     * @return Subscription salva
     * @throws IllegalStateException se status for null após validação
     */
    private Subscription saveWithStatusValidation(Subscription subscription) {
        if (subscription.getStatus() == null) {
            log.error("❌ [VALIDATION] Status é NULL antes de salvar! " +
                    "customerId={}, subscriptionId={}, tenantId={}", 
                    subscription.getStripeCustomerId(),
                    subscription.getStripeSubscriptionId(),
                    subscription.getTenantId());
            throw new IllegalStateException("Subscription status cannot be null before persist!");
        }

        log.debug("[SAVE] Salvando subscription com status={}, customerId={}", 
                subscription.getStatus(), subscription.getStripeCustomerId());

        return subscriptionRepository.save(subscription);
    }
}
