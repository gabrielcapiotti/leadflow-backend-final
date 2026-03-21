package com.leadflow.backend.multitenancy.test;

import com.leadflow.backend.entities.lead.Lead;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.lead.LeadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.EntityManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("🔒 Multi-Tenant Context Isolation Tests")
public class MultiTenantIsolationTest {

    private static final String TENANT_A = "tenant_a";
    private static final String TENANT_B = "tenant_b";
    private static final String TENANT_INVALID = "INVALID!!!"; // Test invalid format

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private EntityManager em;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("✅ TenantContext can be set and retrieved correctly")
    void testTenantContextSetAndRetrieve() {

        TenantContext.setTenant(TENANT_A);
        
        assertEquals(
                TENANT_A,
                TenantContext.getTenant(),
                "Tenant A should be retrievable after set"
        );

        TenantContext.clear();
    }

    @Test
    @DisplayName("✅ TenantContext switching isolates correctly")
    void testTenantContextSwitching() {

        // Set Tenant A
        TenantContext.setTenant(TENANT_A);
        assertEquals(TENANT_A, TenantContext.getTenant());

        // Clean and switch to Tenant B
        TenantContext.clear();
        TenantContext.setTenant(TENANT_B);
        assertEquals(TENANT_B, TenantContext.getTenant());

        // Back to Tenant A
        TenantContext.clear();
        TenantContext.setTenant(TENANT_A);
        assertEquals(TENANT_A, TenantContext.getTenant());

        TenantContext.clear();
    }

    @Test
    @DisplayName("✅ TenantContext prevents double-set in same request")
    void testDoubleSetTenantThrowsError() {

        TenantContext.setTenant(TENANT_A);

        assertThrows(
                IllegalStateException.class,
                () -> TenantContext.setTenant(TENANT_B),
                "Setting tenant twice should be prevented"
        );

        TenantContext.clear();
    }

    @Test
    @DisplayName("✅ TenantContext throws when accessing without set")
    void testGetTenantWithoutSetThrow() {

        TenantContext.clear();

        assertThrows(
                IllegalStateException.class,
                TenantContext::getTenant,
                "Should throw when accessing tenant after clear()"
        );
    }

    @Test
    @DisplayName("✅ TenantContext getOrDefault returns fallback when not set")
    void testGetOrDefaultReturnsFallback() {

        TenantContext.clear();
        
        String result = TenantContext.getOrDefault();
        
        assertEquals(
                "public",
                result,
                "Should return default tenant when context is empty"
        );
    }

    @Test
    @DisplayName("✅ Multiple tenants can be tracked independently")
    void testMultipleTenantTracking() {

        // Tenant A
        TenantContext.setTenant(TENANT_A);
        assertTrue(TenantContext.isSet());
        assertEquals(TENANT_A, TenantContext.getTenant());

        // Switch to Tenant B
        TenantContext.clear();
        TenantContext.setTenant(TENANT_B);
        assertTrue(TenantContext.isSet());
        assertEquals(TENANT_B, TenantContext.getTenant());

        // Verify Tenant B persists
        assertEquals(TENANT_B, TenantContext.getTenant());

        // Clear
        TenantContext.clear();
        assertFalse(TenantContext.isSet());
    }

    @Test
    @DisplayName("🔥 CRITICAL: Tenant B CANNOT read Tenant A's leads from database")
    void testDatabaseLeadIsolationCrossAccess() {

        // ============================================
        // Stage 1: Tenant A creates lead in database
        // ============================================
        TenantContext.setTenant(TENANT_A);
        
        UUID userIdA = UUID.randomUUID();
        Lead leadTenantA = new Lead(
                userIdA,
                "Alice's Lead",
                "alice@example.com",
                "11999999999"
        );
        
        leadRepository.save(leadTenantA);
        em.flush();
        em.clear();
        
        UUID leadIdA = leadTenantA.getId();
        assertEquals(TENANT_A, leadTenantA.getTenantId(), "Lead must have Tenant A set");

        TenantContext.clear();

        // ============================================
        // Stage 2: Tenant B tries to find Tenant A's lead
        // ============================================
        TenantContext.setTenant(TENANT_B);
        
        var result = leadRepository.findById(leadIdA);
        
        assertTrue(
                result.isEmpty(),
                "❌ SECURITY BREACH: Tenant B should NOT see Tenant A's lead"
        );

        TenantContext.clear();
    }

    @Test
    @DisplayName("🔥 CRITICAL: Tenant B CANNOT find Tenant A's lead by count")
    void testDatabaseLeadIsolationCount() {

        // ============================================
        // Stage 1: Populate Tenant A with 3 leads
        // ============================================
        TenantContext.setTenant(TENANT_A);
        
        UUID userIdA = UUID.randomUUID();
        for (int i = 1; i <= 3; i++) {
            Lead lead = new Lead(
                    userIdA,
                    "Lead A-" + i,
                    "lead_a_" + i + "@example.com",
                    "119" + String.format("%07d", i)
            );
            leadRepository.save(lead);
        }
        em.flush();
        em.clear();

        long countA = leadRepository.count();
        assertEquals(3, countA, "Tenant A should have 3 leads");

        TenantContext.clear();

        // ============================================
        // Stage 2: Populate Tenant B with 2 leads
        // ============================================
        TenantContext.setTenant(TENANT_B);
        
        UUID userIdB = UUID.randomUUID();
        for (int i = 1; i <= 2; i++) {
            Lead lead = new Lead(
                    userIdB,
                    "Lead B-" + i,
                    "lead_b_" + i + "@example.com",
                    "128" + String.format("%07d", i)
            );
            leadRepository.save(lead);
        }
        em.flush();
        em.clear();

        long countB = leadRepository.count();
        assertEquals(2, countB, "Tenant B should only see its 2 leads");

        TenantContext.clear();

        // ============================================
        // Stage 3: Verify Tenant A still sees only 3
        // ============================================
        TenantContext.setTenant(TENANT_A);
        
        long countAFinal = leadRepository.count();
        assertEquals(3, countAFinal, "Tenant A must still see exactly 3 leads (isolation verified)");

        TenantContext.clear();
    }
}
