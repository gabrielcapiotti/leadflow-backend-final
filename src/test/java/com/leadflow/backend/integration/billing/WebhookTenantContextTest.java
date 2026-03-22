package com.leadflow.backend.integration.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.leadflow.backend.multitenancy.context.TenantContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests to verify TenantContext is properly set and cleared
 * to prevent tenant data leakage between requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "multitenancy.enabled=true",
    "jwt.secret=0123456789abcdef0123456789abcdef"
})
public class WebhookTenantContextTest {

    @BeforeEach
    public void setUp() {
        // Ensure context is clean before each test
        TenantContext.clear();
    }

    @Test
    public void shouldSetAndRetrieveTenantContext() {
        // Arrange
        String tenantId = "test-tenant-1";

        // Act
        TenantContext.setTenant(tenantId);
        String retrieved = TenantContext.getTenant();

        // Assert
        assertThat(retrieved).isEqualTo(tenantId);
    }

    @Test
    public void shouldClearTenantContextProperly() {
        // Arrange
        String tenantId = "test-tenant-2";
        TenantContext.setTenant(tenantId);
        assertThat(TenantContext.getTenant()).isEqualTo(tenantId);

        // Act
        TenantContext.clear();

        // Assert: Should throw or return null, depending on implementation
        assertThatThrownBy(TenantContext::getTenant)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Tenant not set");
    }

    @Test
    public void shouldSwitchBetweenTenantContexts() {
        // Arrange
        String tenant1 = "tenant-switch-1";
        String tenant2 = "tenant-switch-2";

        // Act & Assert: Set tenant 1
        TenantContext.setTenant(tenant1);
        assertThat(TenantContext.getTenant()).isEqualTo(tenant1);

        // Act: Clear and set tenant 2
        TenantContext.clear();
        TenantContext.setTenant(tenant2);

        // Assert: Now should be tenant 2
        assertThat(TenantContext.getTenant()).isEqualTo(tenant2);
    }

    @Test
    public void shouldNotAllowDoubleSet() {
        // Arrange
        String tenant1 = "first-tenant";
        String tenant2 = "second-tenant";

        TenantContext.setTenant(tenant1);

        // Act & Assert: Setting again without clearing should throw
        assertThatThrownBy(() -> TenantContext.setTenant(tenant2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already set");
    }

    @Test
    public void shouldProvideGetOrDefaultFallback() {
        // Arrange
        TenantContext.clear();

        // Act: getOrDefault() returns DEFAULT_TENANT ("public") when context is not set
        String result = TenantContext.getOrDefault();

        // Assert: Should return default tenant
        assertThat(result).isEqualTo("public");
    }

    @Test
    public void shouldRetainContextFromGetOrDefault() {
        // Arrange: Ensure context is cleared
        TenantContext.clear();

        // Act: Get with fallback returns DEFAULT_TENANT
        String retrieved = TenantContext.getOrDefault();
        assertThat(retrieved).isEqualTo("public");

        // Assert: Fallback does NOT set the context - it only returns the value
        assertThatThrownBy(TenantContext::getTenant)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldIsolateTenantContextBetweenThreads() throws InterruptedException {
        // Arrange
        String tenant1 = "thread-1-tenant";
        String tenant2 = "thread-2-tenant";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> results = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();

        try {
            // Act: Thread 1 sets tenant1
            executor.submit(() -> {
                try {
                    TenantContext.setTenant(tenant1);
                    Thread.sleep(100); // Simulate some work
                    results.add("thread1:" + TenantContext.getTenant());
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });

            // Act: Thread 2 sets tenant2
            executor.submit(() -> {
                try {
                    TenantContext.setTenant(tenant2);
                    Thread.sleep(50); // Sleep less to ensure cross-verification
                    results.add("thread2:" + TenantContext.getTenant());
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });

            // Wait for both threads
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            // Assert: No errors occurred
            assertThat(errors).isEmpty();

            // Assert: Each thread should have its own tenant context
            assertThat(results)
                .contains("thread1:" + tenant1, "thread2:" + tenant2);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void shouldClearContextInFinallyBlock() {
        // Arrange
        String tenantId = "finally-block-test";
        try {
            // Act: Set tenant
            TenantContext.setTenant(tenantId);
            assertThat(TenantContext.getTenant()).isEqualTo(tenantId);

            // Simulate an exception during processing
            throw new RuntimeException("Simulated processing error");
        } catch (RuntimeException e) {
            // Verify we can catch the error
            assertThat(e.getMessage()).contains("Simulated processing error");
        } finally {
            // Act: Clear in finally
            TenantContext.clear();
        }

        // Assert: Context should be cleared even after exception
        assertThatThrownBy(TenantContext::getTenant)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Tenant not set");
    }

    @Test
    public void shouldPreventContextLeakageBetweenRequests() throws InterruptedException {
        // Simulate multiple request cycles
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        List<String> results = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                final int requestNum = i;
                executor.submit(() -> {
                    try {
                        // Simulate request processing
                        String tenant = "tenant-" + requestNum;
                        TenantContext.setTenant(tenant);

                        // Simulate some work
                        Thread.sleep(20);

                        // Verify context is still correct for this thread
                        String result = TenantContext.getTenant();
                        results.add("request-" + requestNum + ":" + result);

                        // Verify isolation
                        assertThat(result).isEqualTo(tenant);
                    } catch (Exception e) {
                        results.add("ERROR-" + requestNum + ":" + e.getMessage());
                    } finally {
                        // Clear context at end of request
                        TenantContext.clear();
                        latch.countDown();
                    }
                });
            }

            // Wait for all requests
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

            // Assert: All requests completed successfully with correct isolation
            assertThat(results)
                .hasSize(3)
                .allMatch(r -> !r.startsWith("ERROR"))
                .contains("request-0:tenant-0", "request-1:tenant-1", "request-2:tenant-2");
        } finally {
            executor.shutdown();
        }
    }
}
