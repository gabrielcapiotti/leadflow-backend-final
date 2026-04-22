package com.leadflow.backend.repository;

import com.leadflow.backend.entities.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByEventId(String eventId);

    Optional<Payment> findByEventId(String eventId);

    // NOVO: Buscar todos os payments com status "paid" (payments realmente pagos)
    List<Payment> findAllByStatus(String status);

    // NOVO: Calcular SUM de todos os payments pagos (fonte de verdade para revenue)
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'paid'")
    BigDecimal sumAllPaidPayments();

    // NOVO: Calcular SUM de payments pagos por tenant
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'paid' AND p.tenantId = :tenantId")
    BigDecimal sumPaidPaymentsByTenant(UUID tenantId);
}