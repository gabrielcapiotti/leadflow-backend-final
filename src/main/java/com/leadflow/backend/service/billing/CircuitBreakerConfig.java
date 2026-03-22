package com.leadflow.backend.service.billing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit Breaker pattern para StripeEventRetryScheduler.
 * 
 * Previne retry infinito ou cascata de falhas ao:
 * - Contar falhas consecutivas
 * - Abrir circuit (OPEN) após threshold
 * - Rejeitar retries enquanto circuit está aberto
 * - Tentar recuperar após timeout (HALF_OPEN)
 * 
 * Estados:
 * - CLOSED (Normal): Aceita retries normalmente
 * - OPEN (Erro): Rejeita retries, aguarda cooldown
 * - HALF_OPEN (Teste): Permite limited retries para testar recuperação
 */
@Component
@Slf4j
public class CircuitBreakerConfig {

    public enum CircuitState {
        CLOSED,      // Normal operation
        OPEN,        // Too many failures, reject new retries
        HALF_OPEN    // Testing if system recovered
    }

    // Configuration constants
    private static final int FAILURE_THRESHOLD = 10;      // Abrir após 10 falhas
    private static final long OPEN_TIMEOUT_SECONDS = 300; // 5 minutos em OPEN
    private static final int HALF_OPEN_ATTEMPTS = 3;     // Permitir 3 tentativas em HALF_OPEN

    // State tracking
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastFailureTime = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastStateChangeTime = new AtomicReference<>(LocalDateTime.now());

    /**
     * Verifica se é permitido tentar retry.
     * 
     * @return true se permitido, false se circuit está OPEN
     */
    public synchronized boolean canAttemptRetry() {
        CircuitState current = state.get();

        if (current == CircuitState.CLOSED) {
            // Normal: sempre permitir
            return true;
        }

        if (current == CircuitState.OPEN) {
            // Verificar se timeout expirou (transição para HALF_OPEN)
            LocalDateTime lastChange = lastStateChangeTime.get();
            long secondsSinceOpen = ChronoUnit.SECONDS.between(lastChange, LocalDateTime.now());

            if (secondsSinceOpen > OPEN_TIMEOUT_SECONDS) {
                log.info("⚠️ Circuit breaker transitioning to HALF_OPEN after {}s timeout", 
                    OPEN_TIMEOUT_SECONDS);
                transitionToHalfOpen();
                return true; // Permitir tentativa em HALF_OPEN
            }

            // Circuit ainda está aberto
            log.warn("❌ Circuit breaker is OPEN. Rejecting retry attempt. ({}s remaining)",
                (OPEN_TIMEOUT_SECONDS - secondsSinceOpen));
            return false;
        }

        if (current == CircuitState.HALF_OPEN) {
            // Em HALF_OPEN: permitir apenas HALF_OPEN_ATTEMPTS antes de reabrir
            if (failureCount.get() < HALF_OPEN_ATTEMPTS) {
                return true;
            }
            // Excedeu tentativas, reabrir circuit
            log.error("❌ Circuit breaker re-opening (max attempts exceeded in HALF_OPEN)");
            transitionToOpen();
            return false;
        }

        return false;
    }

    /**
     * Registrar sucesso no retry.
     * Reduz contador de falhas, possível transição para CLOSED.
     */
    public synchronized void recordSuccess() {
        CircuitState current = state.get();

        failureCount.set(0); // Reset failure counter on success
        successCount.incrementAndGet();

        if (current == CircuitState.HALF_OPEN) {
            log.info("✅ Circuit breaker recovered successfully. Transitioning back to CLOSED");
            transitionToClosed();
        }

        if (current == CircuitState.CLOSED) {
            log.debug("✅ Retry succeeded. Circuit remains CLOSED (failures: {}/{})", 
                failureCount.get(), FAILURE_THRESHOLD);
        }
    }

    /**
     * Registrar falha no retry.
     * Incrementa contador, possível transição para OPEN.
     */
    public synchronized void recordFailure() {
        CircuitState current = state.get();
        int newFailureCount = failureCount.incrementAndGet();
        lastFailureTime.set(LocalDateTime.now());

        if (current == CircuitState.CLOSED) {
            log.warn("⚠️ Webhook retry failed. Failure count: {}/{}", 
                newFailureCount, FAILURE_THRESHOLD);

            if (newFailureCount >= FAILURE_THRESHOLD) {
                log.error("🔴 Circuit breaker OPENING! Threshold reached: {}/{} failures",
                    newFailureCount, FAILURE_THRESHOLD);
                transitionToOpen();
            }
        } else if (current == CircuitState.HALF_OPEN) {
            log.warn("⚠️ Retry failed in HALF_OPEN state (attempt {})", newFailureCount);

            if (newFailureCount >= HALF_OPEN_ATTEMPTS) {
                log.error("❌ Circuit breaker re-opening. Failed during recovery phase");
                transitionToOpen();
            }
        }
    }

    /**
     * Transição para CLOSED (normal operation).
     */
    private void transitionToClosed() {
        synchronized (this) {
            if (state.get() != CircuitState.CLOSED) {
                state.set(CircuitState.CLOSED);
                failureCount.set(0);
                successCount.set(0);
                lastStateChangeTime.set(LocalDateTime.now());

                log.info("🟢 Circuit breaker state: CLOSED (normal operation)");
            }
        }
    }

    /**
     * Transição para OPEN (rejeitando retries).
     */
    private void transitionToOpen() {
        synchronized (this) {
            if (state.get() != CircuitState.OPEN) {
                state.set(CircuitState.OPEN);
                lastStateChangeTime.set(LocalDateTime.now());

                log.error("🔴 Circuit breaker state: OPEN (rejecting retries for {}s)", 
                    OPEN_TIMEOUT_SECONDS);
                
                // TODO: Emit alert event (Fase 3)
                // webhookAlertService.emitAlert(new WebhookAlertEvent(...));
            }
        }
    }

    /**
     * Transição para HALF_OPEN (modo teste).
     */
    private void transitionToHalfOpen() {
        synchronized (this) {
            if (state.get() != CircuitState.HALF_OPEN) {
                state.set(CircuitState.HALF_OPEN);
                failureCount.set(0);
                successCount.set(0);
                lastStateChangeTime.set(LocalDateTime.now());

                log.warn("🟡 Circuit breaker state: HALF_OPEN (testing recovery)");
            }
        }
    }

    /**
     * Retorna o estado atual do circuit breaker.
     */
    public CircuitState getState() {
        return state.get();
    }

    /**
     * Retorna o contador de falhas.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Retorna o contador de sucessos.
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * Retorna o tempo desde a última falha.
     */
    public LocalDateTime getLastFailureTime() {
        return lastFailureTime.get();
    }

    /**
     * Retorna informações de debug.
     */
    @Override
    public String toString() {
        return String.format(
            "CircuitBreaker{state=%s, failures=%d/%d, successes=%d, lastChange=%s}",
            state.get(), failureCount.get(), FAILURE_THRESHOLD, successCount.get(),
            lastStateChangeTime.get()
        );
    }

    /**
     * Reset manual (apenas para testes).
     */
    public synchronized void reset() {
        transitionToClosed();
        log.info("Circuit breaker manually reset");
    }
}
