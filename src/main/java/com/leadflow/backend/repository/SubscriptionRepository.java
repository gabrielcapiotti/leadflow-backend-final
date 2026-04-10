package com.leadflow.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.leadflow.backend.entities.Subscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByStripeCustomerId(String customerId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<Subscription> findByEmailIgnoreCase(String email);
    
    Optional<Subscription> findByTenantId(UUID tenantId);

    @Query("""
        SELECT s FROM Subscription s
        JOIN FETCH s.plan
        WHERE s.tenantId = :tenantId
    """)
    Optional<Subscription> findByTenantIdWithPlanFetch(@Param("tenantId") UUID tenantId);

    Optional<Subscription> findByTenantIdAndStripeSubscriptionId(UUID tenantId, String stripeSubscriptionId);

    @Query("SELECT s FROM Subscription s WHERE s.status = :status " +
           "AND s.expiresAt BETWEEN :startDate AND :endDate " +
           "ORDER BY s.expiresAt ASC")
    List<Subscription> findByStatusAndExpiresAtBetween(
        @Param("status") Subscription.SubscriptionStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT DISTINCT s FROM Subscription s
        JOIN FETCH s.plan
    """)
    List<Subscription> findAllWithPlan();

    @Query("""
        SELECT s FROM Subscription s
        JOIN FETCH s.plan
        WHERE s.tenantId = :tenantId
    """)
    List<Subscription> findAllByTenantIdWithPlan(@Param("tenantId") UUID tenantId);

}