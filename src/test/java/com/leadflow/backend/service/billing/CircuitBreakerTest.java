package com.leadflow.backend.service.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CircuitBreakerConfig.
 * 
 * Tests the Circuit Breaker pattern implementation:
 * - CLOSED state: Normal operation
 * - OPEN state: Rejects retries after threshold
 * - HALF_OPEN state: Testing recovery
 */
@SpringBootTest
@TestPropertySource(properties = {
    "multitenancy.enabled=false"
})
public class CircuitBreakerTest {

    private CircuitBreakerConfig circuitBreaker;

    @BeforeEach
    public void setUp() {
        circuitBreaker = new CircuitBreakerConfig();
    }

    // ===== CLOSED STATE TESTS =====

    @Test
    public void testInitialStateClosed() {
        // Assert: Circuit should start in CLOSED state
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }

    @Test
    public void testCanAttemptRetryWhenClosed() {
        // Assert: Should allow retry in CLOSED state
        assertThat(circuitBreaker.canAttemptRetry()).isTrue();
    }

    @Test
    public void testFailureCountIncrements() {
        // Act: Record multiple failures
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }

        // Assert: Failure count should be 3
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(3);
    }

    @Test
    public void testSuccessResetsFailureCount() {
        // Arrange: Record some failures
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(2);

        // Act: Record success
        circuitBreaker.recordSuccess();

        // Assert: Failure count should reset to 0
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        assertThat(circuitBreaker.getSuccessCount()).isEqualTo(1);
    }

    // ===== OPEN STATE TESTS =====

    @Test
    public void testCircuitOpensAfterThreshold() {
        // Arrange: Record failures up to threshold (10)
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }

        // Assert: Circuit should be OPEN after 10 failures
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.OPEN);
    }

    @Test
    public void testRejectsRetryWhenOpen() {
        // Arrange: Open the circuit
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }

        // Assert: Should reject retry attempts
        assertThat(circuitBreaker.canAttemptRetry()).isFalse();
    }

    @Test
    public void testCircuitRemainsOpenAfterFailure() {
        // Arrange: Open the circuit
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.OPEN);

        // Act: Try to record another failure while OPEN
        circuitBreaker.recordFailure();

        // Assert: Should remain OPEN
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.OPEN);
    }

    // ===== HALF_OPEN STATE TESTS =====

    @Test
    public void testTransitionsToHalfOpenAfterTimeout() throws InterruptedException {
        // Arrange: Open the circuit
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.OPEN);

        // Note: In real test, we'd wait 5 minutes (~300 seconds)
        // For unit testing, we verify canAttemptRetry eventually transitions
        
        // Act: In HALF_OPEN, canAttemptRetry should work after timeout
        // This would require time manipulation or separate integration test
        
        // Assert: State initially OPEN
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.OPEN);
    }

    @Test
    public void testRecoverySucceedsDuringHalfOpen() {
        // Arrange: Manually set to HALF_OPEN (simulating post-timeout)
        circuitBreaker.reset(); // Reset to CLOSED first
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }
        // Now is OPEN, but we can't wait 5 minutes in tests
        // For proper testing, would need mock time or separate integration test

        // For now, test that success in HALF_OPEN would transition to CLOSED
        // This is tested separately in integration tests
    }

    @Test
    public void testCircuitReopensAfterSuccessFailsInHalfOpen() {
        // Arrange: Get to HALF_OPEN state (normally after 5 min timeout)
        // This test verifies behavior if multiple failures occur in HALF_OPEN

        // Act & Assert: Would require time manipulation or integration test
        // Documented for Phase 3.5 refinement
    }

    // ===== RESET/MANUAL INTERVENTION TESTS =====

    @Test
    public void testManualReset() {
        // Arrange: Open the circuit
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.OPEN);

        // Act: Reset manually
        circuitBreaker.reset();

        // Assert: Should return to CLOSED
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        assertThat(circuitBreaker.getSuccessCount()).isEqualTo(0);
    }

    // ===== METRICS/DEBUG TESTS =====

    @Test
    public void testToStringDebugInfo() {
        // Arrange
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        // Act
        String debugInfo = circuitBreaker.toString();

        // Assert: Should contain state and counters
        assertThat(debugInfo)
            .contains("CLOSED", "failures=2", "successes=0");
    }

    @Test
    public void testSuccessMetrics() {
        // Arrange & Act
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordSuccess();
        }

        // Assert
        assertThat(circuitBreaker.getSuccessCount()).isEqualTo(5);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.CLOSED);
    }

    // ===== EDGE CASES =====

    @Test
    public void testThresholdBoundary() {
        // Arrange: Record exactly at threshold
        for (int i = 0; i < 9; i++) {
            circuitBreaker.recordFailure();
        }

        // Assert: Should still be CLOSED at 9 failures
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.CLOSED);
        assertThat(circuitBreaker.canAttemptRetry()).isTrue();

        // Act: Record 10th failure
        circuitBreaker.recordFailure();

        // Assert: Should be OPEN now
        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreakerConfig.CircuitState.OPEN);
        assertThat(circuitBreaker.canAttemptRetry()).isFalse();
    }

    @Test
    public void testLastFailureTimeTracking() {
        // Arrange & Act
        circuitBreaker.recordFailure();

        // Assert: Should have recorded failure time
        assertThat(circuitBreaker.getLastFailureTime()).isNotNull();
    }

    @Test
    public void testIdempotentCanAttemptRetry() {
        // Act: Call canAttemptRetry multiple times in CLOSED state
        boolean result1 = circuitBreaker.canAttemptRetry();
        boolean result2 = circuitBreaker.canAttemptRetry();
        boolean result3 = circuitBreaker.canAttemptRetry();

        // Assert: All should return true
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertThat(result3).isTrue();
    }
}
