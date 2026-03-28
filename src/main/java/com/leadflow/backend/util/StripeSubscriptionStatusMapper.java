package com.leadflow.backend.util;

import com.leadflow.backend.entities.Subscription;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralizado mapper para converter status string do Stripe
 * para enum SubscriptionStatus.
 * 
 * Garante consistência em toda a aplicação.
 * 
 * Mapeamento:
 * - Stripe: "active", "past_due", "canceled", "incomplete", etc.
 * - Java Enum: ACTIVE, PAST_DUE, CANCELLED, INCOMPLETE
 * 
 * Tratamento de erros:
 * - null → INCOMPLETE (seguro)
 * - valor inválido → INCOMPLETE (logar warning)
 * - conversão case-insensitive
 */
@Slf4j
public class StripeSubscriptionStatusMapper {

    /**
     * Converte status string do Stripe para enum SubscriptionStatus.
     * 
     * @param stripeStatus Status from Stripe (e.g., "active", "canceled")
     * @return SubscriptionStatus enum value
     * @throws IllegalArgumentException se status é inválido (com log detalhado)
     */
    public static Subscription.SubscriptionStatus fromStripe(String stripeStatus) {
        if (stripeStatus == null || stripeStatus.isBlank()) {
            log.debug("[STRIPE_MAPPER] Status é null/blank, usando INCOMPLETE como default");
            return Subscription.SubscriptionStatus.INCOMPLETE;
        }

        String normalized = stripeStatus.toLowerCase().trim();
        
        log.debug("[STRIPE_MAPPER] Convertendo Stripe status: '{}' → normalized: '{}'", 
                stripeStatus, normalized);

        Subscription.SubscriptionStatus result = switch (normalized) {
            // ACTIVE - Subscription ativa, pode fazer requisições
            case "active" -> Subscription.SubscriptionStatus.ACTIVE;
            
            // PAST_DUE - Pagamento vencido, subscription ainda válida mas alerta de pagamento
            case "past_due" -> Subscription.SubscriptionStatus.PAST_DUE;
            
            // CANCELLED - Subscription cancelada (note: Stripe usa "canceled", nosso enum usa "CANCELLED")
            case "canceled", "cancelled" -> Subscription.SubscriptionStatus.CANCELLED;
            
            // INCOMPLETE - Subscription em processo, não completada ainda
            case "incomplete", "incomplete_expired" -> Subscription.SubscriptionStatus.INCOMPLETE;
            
            // Default: qualquer outro valor → INCOMPLETE e log warning
            default -> {
                log.warn("[STRIPE_MAPPER] ⚠️ Status desconhecido do Stripe: '{}'. " +
                        "Usando INCOMPLETE como fallback. Por favor, adicione este status ao mapper!", 
                        stripeStatus);
                yield Subscription.SubscriptionStatus.INCOMPLETE;
            }
        };

        log.debug("[STRIPE_MAPPER] Mapeamento completo: Stripe='{}' → Enum='{}' ✓", 
                stripeStatus, result);
        
        return result;
    }

    /**
     * Validação: verifica se um status string é válido de acordo com Stripe.
     * 
     * @param stripeStatus Status to validate
     * @return true se é um status válido do Stripe
     */
    public static boolean isValidStripeStatus(String stripeStatus) {
        if (stripeStatus == null || stripeStatus.isBlank()) {
            return false;
        }
        
        String normalized = stripeStatus.toLowerCase().trim();
        return switch (normalized) {
            case "active", "past_due", "canceled", "cancelled", "incomplete", "incomplete_expired" 
                -> true;
            default -> false;
        };
    }

    /**
     * Diagnóstico: retorna mapeamento legível para logging/monitoramento.
     */
    public static String diagnosticInfo(String stripeStatus, Subscription.SubscriptionStatus enumStatus) {
        return String.format("[SUBSCRIPTION_STATUS] Stripe='%s' → Enum='%s'", 
                stripeStatus, enumStatus);
    }
}
