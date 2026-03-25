package com.leadflow.backend.controller;

import com.leadflow.backend.dto.ai.ChatRequest;
import com.leadflow.backend.entities.vendor.*;
import com.leadflow.backend.security.SubscriptionGuard;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.ai.AiRateLimiter;
import com.leadflow.backend.service.ai.AiService;
import com.leadflow.backend.service.monitoring.AiMetricsService;
import com.leadflow.backend.service.vendor.ConversationService;
import com.leadflow.backend.service.vendor.VendorFeatureService;
import com.leadflow.backend.service.vendor.VendorLeadService;
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

    private final AiService aiService;
    private final ConversationService conversationService;
    private final VendorLeadService vendorLeadService;
    private final SubscriptionGuard subscriptionGuard;
    private final VendorContext vendorContext;
    private final AiRateLimiter aiRateLimiter;
    private final AiMetricsService aiMetricsService;
    private final VendorFeatureService vendorFeatureService;

    public AiController(
            AiService aiService,
            ConversationService conversationService,
            VendorLeadService vendorLeadService,
            SubscriptionGuard subscriptionGuard,
            VendorContext vendorContext,
            AiRateLimiter aiRateLimiter,
            AiMetricsService aiMetricsService,
            VendorFeatureService vendorFeatureService
    ) {
        this.aiService = aiService;
        this.conversationService = conversationService;
        this.vendorLeadService = vendorLeadService;
        this.subscriptionGuard = subscriptionGuard;
        this.vendorContext = vendorContext;
        this.aiRateLimiter = aiRateLimiter;
        this.aiMetricsService = aiMetricsService;
        this.vendorFeatureService = vendorFeatureService;
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

    private void validateVendorLeadAccess(UUID leadId) {
        vendorLeadService.getLeadForCurrentVendor(leadId);
    }

    // =========================================================
    // CHAT
    // =========================================================
    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChatRequest request
    ) {

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mensagem não pode estar vazia");
        }

        Vendor vendor = validateAiAccess(VendorFeatureKey.AI_CHAT);

        UUID leadId = request.getLeadId();
        validateVendorLeadAccess(leadId);

        conversationService.saveMessage(
                leadId,
                ConversationRole.USER.name(),
                request.getMessage()
        );

        List<VendorLeadConversation> history =
                conversationService.getConversation(leadId);

        String context = history == null
                ? ""
                : history.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        aiMetricsService.increment();

        String aiResponse = aiService.generate(context);

        conversationService.saveMessage(
                leadId,
                ConversationRole.ASSISTANT.name(),
                aiResponse
        );

        return ResponseEntity.ok(Map.of("response", aiResponse));
    }

    // =========================================================
    // SUMMARY
    // =========================================================
    @PostMapping("/lead-summary")
    public ResponseEntity<?> summary(
            @RequestParam UUID leadId
    ) {
        validateAiAccess(VendorFeatureKey.AI_SUMMARY);
        validateVendorLeadAccess(leadId);

        aiMetricsService.increment();
        return ResponseEntity.ok(
                Map.of("summary", aiService.generateSummary(leadId))
        );
    }

    // =========================================================
    // TITLE
    // =========================================================
    @PostMapping("/title-suggestion")
    public ResponseEntity<?> title(
            @RequestParam UUID leadId,
            @RequestParam(required = false) String context
    ) {
        validateAiAccess(VendorFeatureKey.AI_TITLE);

        aiMetricsService.increment();

        String title = (context != null && !context.isBlank())
                ? aiService.suggestTitle(context)
                : aiService.suggestTitle(leadId);

        return ResponseEntity.ok(Map.of("title", title));
    }

    // =========================================================
    // REFINE
    // =========================================================
    @PostMapping("/refine-message")
    public ResponseEntity<?> refine(
            @RequestParam String message
    ) {
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
    public ResponseEntity<?> sentiment(
            @RequestParam UUID leadId
    ) {
        validateAiAccess(VendorFeatureKey.AI_SENTIMENT);
        validateVendorLeadAccess(leadId);

        aiMetricsService.increment();
        return ResponseEntity.ok(
                aiService.analyzeSentiment(leadId)
        );
    }

    // =========================================================
    // CLASSIFY
    // =========================================================
    @PostMapping("/classify-lead")
    public ResponseEntity<?> classify(
            @RequestParam UUID leadId
    ) {
        validateAiAccess(VendorFeatureKey.AI_CLASSIFY);
        validateVendorLeadAccess(leadId);

        aiMetricsService.increment();
        return ResponseEntity.ok(
                aiService.classifyLead(leadId)
        );
    }

    // =========================================================
    // GENERATE RESPONSE
    // =========================================================
    @PostMapping("/generate-response")
    public ResponseEntity<?> generate(
            @RequestParam UUID leadId,
            @RequestParam String prompt
    ) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Prompt não pode estar vazio");
        }

        validateAiAccess(VendorFeatureKey.AI_GENERATE);
        validateVendorLeadAccess(leadId);

        aiMetricsService.increment();

        return ResponseEntity.ok(
                Map.of("response",
                        aiService.generateResponse(leadId, prompt))
        );
    }
}