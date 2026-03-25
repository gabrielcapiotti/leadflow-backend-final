package com.leadflow.backend.service.ai.impl;

import com.leadflow.backend.audit.Audit;
import com.leadflow.backend.quota.CheckQuota;
import com.leadflow.backend.service.ai.AiService;
import com.leadflow.backend.service.monitoring.MetricsService;
import com.leadflow.backend.service.monitoring.SystemAlertService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;
import java.util.Map;

@Service
public class OpenAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private static final String SYSTEM_PROMPT = """
        Você é um assistente comercial especializado em qualificação de leads.

        Extraia os seguintes campos da conversa:

        - nomeCompleto
        - whatsapp
        - tipoConsorcio
        - valorCredito
        - urgencia (quero_fechar | analisando | pesquisando)

        Responda EXCLUSIVAMENTE em JSON válido.
        """;

    private static final String FALLBACK_RESPONSE = """
        {
          "resumo": "IA indisponível no momento.",
          "nivelInteresse": 50,
          "probabilidadeFechamento": 40,
          "recomendacao": "Manter follow-up manual.",
          "stageSugerido": "CONTATO"
        }
        """;

    private final MetricsService metricsService;
    private final SystemAlertService systemAlertService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private final String apiKey;
    private final String model;
    private final String endpoint;

    public OpenAiService(
            MetricsService metricsService,
            SystemAlertService systemAlertService,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.endpoint:https://api.openai.com/v1/chat/completions}") String endpoint
    ) {
        this.metricsService = metricsService;
        this.systemAlertService = systemAlertService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = endpoint;
    }

    @Override
    @Audit(action = "AI_EXECUTION", entity = "AI")
    @CheckQuota(type = "IA_EXECUTION")
    @Retryable(
            retryFor = {
                    HttpServerErrorException.class,
                    ResourceAccessException.class
            },
            maxAttemptsExpression = "#{${openai.retry.max-attempts:3}}",
            backoff = @Backoff(
                    delayExpression = "#{${openai.retry.initial-backoff-ms:1500}}",
                    multiplierExpression = "#{${openai.retry.multiplier:2.0}}"
            )
    )
    public String generate(String prompt) {

        metricsService.incrementAiExecutions();

        if (prompt == null || prompt.isBlank()) {
            log.warn("Prompt vazio recebido pela IA");
            return FALLBACK_RESPONSE;
        }

        log.info("ai_execution prompt_size={}", prompt.length());

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API key não configurada — utilizando fallback");
            return FALLBACK_RESPONSE;
        }

        try {

            String safeApiKey = Objects.requireNonNull(apiKey);
            String safeEndpoint = Objects.requireNonNull(endpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(safeApiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", new Object[]{
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", prompt)
                    }
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(safeEndpoint, request, String.class);

            if (response.getBody() == null) {
                log.warn("Resposta vazia da OpenAI");
                return FALLBACK_RESPONSE;
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            JsonNode contentNode = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode()) {
                log.warn("Resposta inválida da OpenAI");
                return FALLBACK_RESPONSE;
            }

            String content = contentNode.asText();

            if (content == null || content.isBlank()) {
                log.warn("Conteúdo vazio retornado pela OpenAI");
                return FALLBACK_RESPONSE;
            }

            return content;

        } catch (HttpClientErrorException.TooManyRequests ex) {

            log.warn("OpenAI quota exceeded - usando fallback seguro");

            metricsService.incrementAiFailures();

            systemAlertService.sendCriticalAlert(
                    "Quota OpenAI excedida: " + ex.getMessage()
            );

            return FALLBACK_RESPONSE;

        } catch (HttpClientErrorException ex) {

            log.warn("OpenAI client error (4xx): {} - {}", ex.getStatusCode(), ex.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    ex.getStatusCode(),
                    ex.getMessage()
            );

        } catch (HttpServerErrorException |
                 ResourceAccessException e) {

            log.warn("Erro temporário OpenAI (retry): {}", e.getMessage());
            throw e;

        } catch (Exception e) {

            log.error("Erro inesperado ao executar IA", e);

            systemAlertService.sendCriticalAlert(
                    "Falha no serviço de IA: " + e.getMessage()
            );

            return FALLBACK_RESPONSE;
        }
    }

    @Recover
    public String recoverOpenAi(Throwable e, String prompt) {

        log.error("Falha definitiva na IA após retries", e);

        systemAlertService.sendCriticalAlert(
                "Falha no serviço de IA após retentativas: " + e.getMessage()
        );

        return FALLBACK_RESPONSE;
    }

    @Override
    public String generateSummary(java.util.UUID leadId) {
        String prompt = "Gere um resumo breve do lead com ID: " + leadId;
        return generate(prompt);
    }

    @Override
    public String suggestTitle(java.util.UUID leadId) {
        String prompt = "Sugira um título representativo para o lead com ID: " + leadId;
        return generate(prompt);
    }

    @Override
    public String suggestTitle(String context) {
        String prompt = "Baseado no contexto a seguir, sugira um título conciso:\n" + context;
        return generate(prompt);
    }

    @Override
    public String refineMessage(String message) {
        String prompt = "Refine e melhore a seguinte mensagem, mantendo o significado:\n" + message;
        return generate(prompt);
    }

    @Override
    public Map<String, Object> analyzeSentiment(java.util.UUID leadId) {
        String prompt = "Analise o sentimento do lead com ID: " + leadId + " e retorne em JSON com campos: sentiment (positivo/neutro/negativo), score (0-100)";
        String result = generate(prompt);
        return Map.of(
                "sentiment", "neutral",
                "score", 50,
                "analysis", result
        );
    }

    @Override
    public Map<String, Object> classifyLead(java.util.UUID leadId) {
        String prompt = "Classifique o lead com ID: " + leadId + " em hot/warm/cold baseado em seu comportamento e retorne em JSON";
        String result = generate(prompt);
        return Map.of(
                "classification", "warm",
                "confidence", 0.75,
                "details", result
        );
    }

    @Override
    public String generateResponse(java.util.UUID leadId, String prompt) {
        String fullPrompt = "Para o lead ID: " + leadId + ", responda o seguinte:\n" + prompt;
        return generate(fullPrompt);
    }
}