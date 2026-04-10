package com.leadflow.backend.service.vendor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadflow.backend.audit.Audit;
import com.leadflow.backend.dto.vendor.CreateLeadRequest;
import com.leadflow.backend.dto.vendor.StageConversionResponse;
import com.leadflow.backend.dto.vendor.StageTimeMetricsResponse;
import com.leadflow.backend.dto.vendor.VendorLeadMetricsResponse;
import com.leadflow.backend.entities.vendor.LeadStage;
import com.leadflow.backend.entities.vendor.VendorLead;
import com.leadflow.backend.entities.vendor.VendorLeadConversation;
import com.leadflow.backend.entities.vendor.VendorLeadStageHistory;
import com.leadflow.backend.exception.ResourceNotFoundException;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.VendorLeadRepository;
import com.leadflow.backend.repository.VendorLeadStageHistoryRepository;
import com.leadflow.backend.repository.vendor.VendorLeadConversationRepository;
import com.leadflow.backend.quota.CheckQuota;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.monitoring.MetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import com.leadflow.backend.entities.vendor.Vendor;

@Service
public class VendorLeadService {

    private static final Logger log = LoggerFactory.getLogger(VendorLeadService.class);

    private static final Pattern WHATSAPP_PATTERN =
            Pattern.compile("^[0-9+()\\-\\s]{8,20}$");

    private static final Pattern VALOR_CREDITO_PATTERN =
            Pattern.compile("^[0-9.,\\s]{1,30}$");

    private final VendorLeadRepository repository;
    private final VendorLeadConversationRepository conversationRepository;
    private final VendorLeadStageHistoryRepository historyRepository;
    private final VendorContext vendorContext;
    private final MetricsService metricsService;
    private final UsageService usageService;
    private final ObjectMapper objectMapper;

    public VendorLeadService(
            VendorLeadRepository repository,
            VendorLeadConversationRepository conversationRepository,
            VendorLeadStageHistoryRepository historyRepository,
            VendorContext vendorContext,
            MetricsService metricsService,
            UsageService usageService,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.conversationRepository = conversationRepository;
        this.historyRepository = historyRepository;
        this.vendorContext = vendorContext;
        this.metricsService = metricsService;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
    }

    /* ======================================================
       CREATE FROM AI
       ====================================================== */

    @Audit(action = "CREATE_LEAD_FROM_AI", entity = "VendorLead")
    @CheckQuota(type = "LEAD_CREATION")
    public VendorLead createFromAi(UUID vendorId, String json) {

        try {
            JsonNode node = objectMapper.readTree(json);

            if (node.hasNonNull("nomeCompleto") &&
                node.hasNonNull("whatsapp")) {

                String nomeCompleto = sanitizeNomeCompleto(node.get("nomeCompleto").asText());
                String whatsapp = sanitizeWhatsapp(node.get("whatsapp").asText());
                BigDecimal valorCredito = sanitizeAndConvertValorCredito(node.path("valorCredito").asText(null));

                if (nomeCompleto == null || whatsapp == null) {
                    return null;
                }

                var existingLead =
                        repository.findFirstByVendorIdAndWhatsappOrderByCreatedDateDesc(
                                vendorId, whatsapp);

                boolean isNewLead = existingLead.isEmpty();

                VendorLead lead = existingLead.orElseGet(VendorLead::new);

                if (lead.getVendorId() == null) {
                    lead.setVendorId(vendorId);
                }

                if (lead.getTenantId() == null) {
                    lead.setTenantId(TenantContext.requireTenant());
                }

                lead.setNomeCompleto(nomeCompleto);
                lead.setWhatsapp(whatsapp);
                lead.setTipoConsorcio(node.path("tipoConsorcio").asText(null));
                lead.setValorCredito(valorCredito);
                lead.setUrgencia(node.path("urgencia").asText(null));
                lead.setScore(calculateScore(lead));

                if (isNewLead) {
                    usageService.consumeLead(vendorId);
                }

                VendorLead savedLead = repository.save(lead);

                if (isNewLead) {
                    metricsService.incrementLeadCreated();
                    metricsService.incrementLeadCreated(vendorId.toString());

                    log.info("lead_created vendor={} lead={}", vendorId, savedLead.getId());
                }

                return savedLead;
            }

        } catch (Exception e) {
            log.error("error_creating_lead_from_ai vendor={} json={}", vendorId, json, e);
        }

        return null;
    }

    /* ======================================================
       CREATE
       ====================================================== */

    @Audit(action = "CREATE_LEAD", entity = "VendorLead")
    @CheckQuota(type = "LEAD_CREATION")
    @Transactional
    public VendorLead create(CreateLeadRequest request) {

        Vendor vendor = vendorContext.getCurrentVendor(); // fonte única
        UUID vendorId = vendor.getId();
        UUID tenantId = TenantContext.requireTenant();

        String nomeCompleto = sanitizeNomeCompleto(request.getNomeCompleto());
        String whatsapp = sanitizeWhatsapp(request.getWhatsapp());
        BigDecimal valorCredito = sanitizeAndConvertValorCredito(request.getValorCredito());

        if (nomeCompleto == null || whatsapp == null) {
            throw new IllegalArgumentException("Dados do lead inválidos");
        }

        VendorLead lead = new VendorLead();

        lead.setTenantId(tenantId);
        lead.setVendorId(vendorId);
        lead.setNomeCompleto(nomeCompleto);
        lead.setWhatsapp(whatsapp);
        lead.setTipoConsorcio(request.getTipoConsorcio());
        lead.setValorCredito(valorCredito);
        lead.setUrgencia(request.getUrgencia());
        lead.setScore(calculateScore(lead));

        usageService.consumeLead(vendorId);

        VendorLead savedLead = repository.save(lead);

        metricsService.incrementLeadCreated();
        metricsService.incrementLeadCreated(vendorId.toString());

        log.info("lead_created vendor={} lead={}", vendorId, savedLead.getId());

        return savedLead;
    }

    /* ======================================================
       RESTANTE (inalterado, apenas consistente)
       ====================================================== */

    public Page<VendorLead> listForCurrentVendor(Pageable pageable) {
        UUID vendorId = vendorContext.getCurrentVendor().getId();
        return repository.findByVendorId(vendorId, pageable);
    }

    public VendorLead getLeadForCurrentVendor(UUID leadId) {
        UUID vendorId = vendorContext.getCurrentVendor().getId();
        return repository.findByIdAndVendorId(leadId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead não encontrado ou acesso negado"));
    }

    public void deleteLead(UUID leadId) {
        UUID vendorId = vendorContext.getCurrentVendor().getId();

        VendorLead lead = repository.findByIdAndVendorId(leadId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead não encontrado ou acesso negado"));

        if (lead != null) {
            repository.delete(lead);
        }
    }

    private String sanitizeNomeCompleto(String value) {
        if (value == null) return null;
        String sanitized = value.trim().replaceAll("\\s+", " ");
        return (sanitized.length() < 2 || sanitized.length() > 100) ? null : sanitized;
    }

    private String sanitizeWhatsapp(String value) {
        if (value == null) return null;
        String sanitized = value.trim();
        return WHATSAPP_PATTERN.matcher(sanitized).matches() ? sanitized : null;
    }

    private String sanitizeValorCredito(String value) {
        if (value == null) return null;
        String sanitized = value.trim();
        if (sanitized.isBlank()) return null;
        return VALOR_CREDITO_PATTERN.matcher(sanitized).matches() ? sanitized : null;
    }

    private BigDecimal sanitizeAndConvertValorCredito(Object value) {
        if (value == null) return null;
        
        String strValue;
        if (value instanceof BigDecimal) {
            strValue = value.toString();
        } else {
            strValue = value.toString();
        }
        
        String sanitized = sanitizeValorCredito(strValue);
        if (sanitized == null) return null;
        
        try {
            return new BigDecimal(sanitized.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            log.warn("Failed to convert valor_credito: {}", sanitized);
            return null;
        }
    }

    private int calculateScore(VendorLead lead) {

        int base =
                switch (lead.getUrgencia() == null ? "" : lead.getUrgencia()) {
                    case "quero_fechar" -> 100;
                    case "analisando" -> 60;
                    case "pesquisando" -> 30;
                    default -> 10;
                };

        int bonus = 0;

        if (lead.getStage() != null) {
            bonus =
                    switch (lead.getStage()) {
                        case PROPOSTA -> 20;
                        case CONTATO -> 10;
                        default -> 0;
                    };
        }

        return Math.min(base + bonus, 100);
    }

    /* ======================================================
       UPDATE
       ====================================================== */

    @Audit(action = "UPDATE_STAGE", entity = "VendorLead")
    @Transactional
    public VendorLead updateStage(UUID leadId, LeadStage newStage) {
        UUID vendorId = vendorContext.getCurrentVendor().getId();

        VendorLead lead = repository.findByIdAndVendorId(leadId, vendorId)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado ou acesso negado"));

        LeadStage oldStage = lead.getStage();
        lead.setStage(newStage);

        VendorLead updated = repository.save(lead);

        // Record stage history
        VendorLeadStageHistory history = new VendorLeadStageHistory();
        history.setVendorLeadId(leadId);
        history.setPreviousStage(oldStage != null ? oldStage.toString() : null);
        history.setNewStage(newStage != null ? newStage.toString() : null);
        historyRepository.save(history);

        log.info("stage_updated lead={} vendor={} old={} new={}", leadId, vendorId, oldStage, newStage);

        return updated;
    }

    @Audit(action = "ASSIGN_OWNER", entity = "VendorLead")
    @Transactional
    public VendorLead assignOwner(UUID leadId) {
        UUID vendorId = vendorContext.getCurrentVendor().getId();

        VendorLead lead = repository.findByIdAndVendorId(leadId, vendorId)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado ou acesso negado"));

        // Mark as owned (could include user ID if needed)
        lead.setStage(LeadStage.CONTATO);

        VendorLead updated = repository.save(lead);

        log.info("owner_assigned lead={} vendor={}", leadId, vendorId);

        return updated;
    }

    /* ======================================================
       METRICS
       ====================================================== */

    public VendorLeadMetricsResponse getMetricsForCurrentVendor() {
        UUID vendorId = vendorContext.getCurrentVendor().getId();

        List<VendorLead> leads = repository.findByVendorId(vendorId);

        Map<String, Long> stagesMap = new HashMap<>();
        stagesMap.put("total", (long) leads.size());
        stagesMap.put("CONTATO", leads.stream().filter(l -> l.getStage() == LeadStage.CONTATO).count());
        stagesMap.put("PROPOSTA", leads.stream().filter(l -> l.getStage() == LeadStage.PROPOSTA).count());
        stagesMap.put("FECHADO", leads.stream().filter(l -> l.getStage() == LeadStage.FECHADO).count());

        return new VendorLeadMetricsResponse(stagesMap);
    }

    public StageTimeMetricsResponse calculateAverageStageTimeForCurrentVendor() {
        UUID vendorId = vendorContext.getCurrentVendor().getId();

        List<VendorLead> leads = repository.findByVendorId(vendorId);

        Map<String, Double> timeMetrics = new HashMap<>();
        timeMetrics.put("average_stage_time_hours", 0.0);

        // Aggregate stage time metrics per lead
        for (VendorLead lead : leads) {
            List<VendorLeadStageHistory> histories = 
                historyRepository.findByVendorLeadIdOrderByChangedAtDesc(lead.getId());
            
            if (!histories.isEmpty()) {
                long hours = histories.size(); // simplified: use count as proxy for hours spent
                timeMetrics.merge("average_stage_time_hours", (double) hours / leads.size(), Double::sum);
            }
        }

        return new StageTimeMetricsResponse(timeMetrics);
    }

    public StageConversionResponse calculateConversionRatesForCurrentVendor() {
        UUID vendorId = vendorContext.getCurrentVendor().getId();

        List<VendorLead> leads = repository.findByVendorId(vendorId);
        long total = leads.size();

        Map<String, Double> conversionMap = new HashMap<>();

        if (total == 0) {
            conversionMap.put("CONTATO_rate", 0.0);
            conversionMap.put("PROPOSTA_rate", 0.0);
            conversionMap.put("FECHADO_rate", 0.0);
            return new StageConversionResponse(conversionMap);
        }

        long contatoCount = leads.stream().filter(l -> l.getStage() == LeadStage.CONTATO).count();
        long propostaCount = leads.stream().filter(l -> l.getStage() == LeadStage.PROPOSTA).count();
        long fechadoCount = leads.stream().filter(l -> l.getStage() == LeadStage.FECHADO).count();

        conversionMap.put("CONTATO_rate", (contatoCount * 100.0) / total);
        conversionMap.put("PROPOSTA_rate", (propostaCount * 100.0) / total);
        conversionMap.put("FECHADO_rate", (fechadoCount * 100.0) / total);

        return new StageConversionResponse(conversionMap);
    }

    /* ======================================================
       QUERY/READ
       ====================================================== */

    public List<VendorLead> getRankingForCurrentVendor() {
        UUID vendorId = vendorContext.getCurrentVendor().getId();

        return repository.findByVendorIdOrderByScoreDesc(vendorId);
    }

    public List<VendorLeadConversation> getConversation(UUID leadId) {
        return conversationRepository.findByVendorLeadIdOrderByCreatedAtAsc(leadId);
    }

    /* ======================================================
       CONVERSATION
       ====================================================== */

    @Transactional
    public void saveConversation(UUID leadId, String userMessage, String assistantMessage) {
        if (userMessage != null) {
            VendorLeadConversation userConv = new VendorLeadConversation();
            userConv.setVendorLeadId(leadId);
            userConv.setContent(userMessage);
            userConv.setSender("USER");
            conversationRepository.save(userConv);
        }

        if (assistantMessage != null) {
            VendorLeadConversation assistantConv = new VendorLeadConversation();
            assistantConv.setVendorLeadId(leadId);
            assistantConv.setContent(assistantMessage);
            assistantConv.setSender("ASSISTANT");
            conversationRepository.save(assistantConv);
        }
    }
}