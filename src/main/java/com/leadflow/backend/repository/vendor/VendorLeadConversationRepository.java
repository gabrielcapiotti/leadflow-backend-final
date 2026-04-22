package com.leadflow.backend.repository.vendor;

import com.leadflow.backend.entities.vendor.VendorLeadConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorLeadConversationRepository
        extends JpaRepository<VendorLeadConversation, UUID> {

    /**
     * Recupera todo o histórico de conversa de um lead
     * ordenado cronologicamente.
     */
    List<VendorLeadConversation> findByVendorLeadIdOrderByCreatedAtAsc(UUID vendorLeadId);

    /**
     * Recupera histórico paginado de conversa de um lead
     * ordenado cronologicamente.
     */
    Page<VendorLeadConversation> findByVendorLeadIdOrderByCreatedAtAsc(
            UUID vendorLeadId,
            Pageable pageable
    );

    /**
     * Recupera uma mensagem específica validando o VendorLead.
     * Evita acesso cruzado entre conversas de outros vendors.
     */
    Optional<VendorLeadConversation> findByIdAndVendorLeadId(
            UUID id,
            UUID vendorLeadId
    );

    /**
     * Conta quantas mensagens existem para um vendor lead.
     * Útil para limitar contexto enviado para LLM.
     */
    long countByVendorLeadId(UUID vendorLeadId);

    /**
     * Recupera mensagens de um vendor lead dentro de um intervalo de tempo
     * ordenadas cronologicamente.
     */
    List<VendorLeadConversation> findByVendorLeadIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID vendorLeadId,
            Instant start,
            Instant end
    );
}