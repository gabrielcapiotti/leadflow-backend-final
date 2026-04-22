package com.leadflow.backend.controller;

import com.leadflow.backend.dto.ai.ChatRequest;
import com.leadflow.backend.entities.vendor.*;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.service.billing.RequiresBilling;
import com.leadflow.backend.repository.VendorLeadRepository;
import com.leadflow.backend.security.SubscriptionGuard;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.ai.AiRateLimiter;
import com.leadflow.backend.service.ai.AiService;
import com.leadflow.backend.service.monitoring.AiMetricsService;
import com.leadflow.backend.service.vendor.ConversationService;
import com.leadflow.backend.service.vendor.VendorFeatureService;
import com.leadflow.backend.service.vendor.VendorLeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai")
@PreAuthorize("@subscriptionGuard.isActive()")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;
    private final ConversationService conversationService;
    private final VendorLeadService vendorLeadService;
    private final SubscriptionGuard subscriptionGuard;
    private final VendorContext vendorContext;
    private final AiRateLimiter aiRateLimiter;
    private final AiMetricsService aiMetricsService;
    private final VendorFeatureService vendorFeatureService;
    private final VendorLeadRepository vendorLeadRepository;

    public AiController(
            AiService aiService,
            ConversationService conversationService,
            VendorLeadService vendorLeadService,
            SubscriptionGuard subscriptionGuard,
            VendorContext vendorContext,
            AiRateLimiter aiRateLimiter,
            AiMetricsService aiMetricsService,
            VendorFeatureService vendorFeatureService,
            VendorLeadRepository vendorLeadRepository
    ) {
        this.aiService = aiService;
        this.conversationService = conversationService;
        this.vendorLeadService = vendorLeadService;
        this.subscriptionGuard = subscriptionGuard;
        this.vendorContext = vendorContext;
        this.aiRateLimiter = aiRateLimiter;
        this.aiMetricsService = aiMetricsService;
        this.vendorFeatureService = vendorFeatureService;
        this.vendorLeadRepository = vendorLeadRepository;
    }

    // =========================================================
    // CORE VALIDATION (CENTRALIZADO)
    // =========================================================
    private Vendor validateAiAccess(VendorFeatureKey feature) {

        if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Assinatura não permite uso da IA");
        }

        Vendor vendor = vendorContext.getCurrentVendor();

        if (vendor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Vendor não autenticado");
        }

        if (!vendorFeatureService.isEnabled(vendor.getId(), feature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Recurso não habilitado para esta conta");
        }

        if (!aiRateLimiter.allow(vendor.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de uso temporário atingido");
        }

        return vendor;
    }

    /**
     * 🔥 CORREÇÃO CRÍTICA: Valida que o VendorLead pertence ao tenant autenticado
     * Usa TenantContext (do JWT) e VendorLeadRepository
     */
    private void validateVendorLeadAccess(UUID vendorLeadId) {
        UUID tenantId = TenantContext.getTenant();
        
        if (tenantId == null) {
            log.error("❌ TenantContext vazio durante validação de VendorLead");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Tenant não autenticado");
        }

        // Busca VendorLead com tenantId explícito (multi-tenancy safety)
        boolean exists = vendorLeadRepository.findById(vendorLeadId)
                .filter(vl -> vl.getTenantId() != null && vl.getTenantId().equals(tenantId))
                .isPresent();

        if (!exists) {
            log.warn("🚫 VendorLead {} não encontrado ou acesso negado para tenant {}", vendorLeadId, tenantId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "VendorLead não encontrado ou acesso negado");
        }
        
        log.debug("✅ VendorLead {} validado com sucesso para tenant {}", vendorLeadId, tenantId);
    }

    // =========================================================
    // CHAT
    // =========================================================
    @PostMapping("/chat")
    @RequiresBilling
    public ResponseEntity<?> chat(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChatRequest request
    ) {

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mensagem não pode estar vazia");
        }

        Vendor vendor = validateAiAccess(VendorFeatureKey.AI_CHAT);

        UUID vendorLeadId = request.getVendorLeadId();
        validateVendorLeadAccess(vendorLeadId);

        conversationService.saveMessage(
                vendorLeadId,
                ConversationRole.USER.name(),
                request.getMessage()
        );

        List<VendorLeadConversation> history =
                conversationService.getConversation(vendorLeadId);

        String context = history == null
                ? ""
                : history.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        aiMetricsService.increment();

        String aiResponse = aiService.generate(context);

        conversationService.saveMessage(
                vendorLeadId,
                ConversationRole.ASSISTANT.name(),
                aiResponse
        );

        return ResponseEntity.ok(Map.of("response", aiResponse));
    }

    // =========================================================
    // SUMMARY
    // =========================================================
    @PostMapping("/lead-summary")
    @RequiresBilling
    public ResponseEntity<?> summary(
            @Valid @RequestBody Map<String, Object> request
    ) {
        UUID vendorLeadId = UUID.fromString(request.get("vendorLeadId").toString());
        validateAiAccess(VendorFeatureKey.AI_SUMMARY);
        validateVendorLeadAccess(vendorLeadId);

        aiMetricsService.increment();
        return ResponseEntity.ok(
                Map.of("summary", aiService.generateSummary(vendorLeadId))
        );
    }

    // =========================================================
    // TITLE
    // =========================================================
    @PostMapping("/title-suggestion")
    @RequiresBilling
    public ResponseEntity<?> title(
            @Valid @RequestBody Map<String, Object> request
    ) {
        UUID vendorLeadId = UUID.fromString(request.get("vendorLeadId").toString());
        String context = request.containsKey("context") ? request.get("context").toString() : null;
        
        validateAiAccess(VendorFeatureKey.AI_TITLE);
        validateVendorLeadAccess(vendorLeadId);

        aiMetricsService.increment();

        String title = (context != null && !context.isBlank())
                ? aiService.suggestTitle(context)
                : aiService.suggestTitle(vendorLeadId);

        return ResponseEntity.ok(Map.of("title", title));
    }

    // =========================================================
    // REFINE
    // =========================================================
    @PostMapping("/refine-message")
    @RequiresBilling
    public ResponseEntity<?> refine(
            @Valid @RequestBody Map<String, Object> request
    ) {
        String message = request.get("message").toString();
        
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mensagem não pode estar vazia");
        }

        validateAiAccess(VendorFeatureKey.AI_REFINE);

        aiMetricsService.increment();
        return ResponseEntity.ok(
                Map.of("refined", aiService.refineMessage(message))
        );
    }

    // =========================================================
    // SENTIMENT
    // =========================================================
    @PostMapping("/sentiment-analysis")
    @RequiresBilling
    public ResponseEntity<?> sentiment(
            @Valid @RequestBody Map<String, Object> request
    ) {
        UUID vendorLeadId = UUID.fromString(request.get("vendorLeadId").toString());
        
        validateAiAccess(VendorFeatureKey.AI_SENTIMENT);
        validateVendorLeadAccess(vendorLeadId);

        aiMetricsService.increment();
        return ResponseEntity.ok(
                aiService.analyzeSentiment(vendorLeadId)
        );
    }

    // =========================================================
    // CLASSIFY
    // =========================================================
    @PostMapping("/classify-lead")
    @RequiresBilling
    public ResponseEntity<?> classify(
            @Valid @RequestBody Map<String, Object> request
    ) {
        UUID vendorLeadId = UUID.fromString(request.get("vendorLeadId").toString());
        
        validateAiAccess(VendorFeatureKey.AI_CLASSIFY);
        validateVendorLeadAccess(vendorLeadId);

        aiMetricsService.increment();
        return ResponseEntity.ok(
                aiService.classifyLead(vendorLeadId)
        );
    }

    // =========================================================
    // GENERATE RESPONSE
    // =========================================================
    @PostMapping("/generate-response")
    @RequiresBilling
    public ResponseEntity<?> generate(
            @Valid @RequestBody Map<String, Object> request
    ) {
        UUID vendorLeadId = UUID.fromString(request.get("vendorLeadId").toString());
        String prompt = request.get("prompt").toString();
        
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Prompt não pode estar vazio");
        }

        validateAiAccess(VendorFeatureKey.AI_GENERATE);
        validateVendorLeadAccess(vendorLeadId);

        aiMetricsService.increment();

        return ResponseEntity.ok(
                Map.of("response",
                        aiService.generateResponse(vendorLeadId, prompt))
        );
    }
}