package com.leadflow.backend.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.model.Event;
import lombok.extern.slf4j.Slf4j;

/**
 * Extrai dados de eventos Stripe DIRETAMENTE do JSON bruto.
 * 
 * NÃO usa SDK deserialization (que falha com payloads mockados).
 * 
 * Workflow:
 * 1. Recebe Event do Webhook.constructEvent() - apenas para validação assinatura
 * 2. Extrai getRawJson() do event.data.object
 * 3. Parse JSON com Gson
 * 4. Extrai campos necessários
 * 
 * Benefícios:
 * ✅ Funciona com payloads reais DO Stripe
 * ✅ Funciona com payloads mockados (testes)
 * ✅ Sem dependência do SDK deserialization
 * ✅ Robusto para campos faltantes
 */
@Slf4j
public class StripeEventJsonExtractor {

    /**
     * Extrai JSON bruto do objeto data dentro do evento
     * 
     * Estrutura:
     * {
     *   "type": "invoice.payment_succeeded",
     *   "data": {
     *     "object": { ... }  <- este é o rawJson extraído
     *   }
     * }
     */
    public static JsonObject extractDataObjectAsJson(Event event) {
        try {
            if (event == null) {
                log.warn("[EXTRACTOR] Event is null");
                return null;
            }

            // Tenta pela forma correta 1: getRawJson do event.data
            String rawJson = event.getDataObjectDeserializer().getRawJson();
            if (rawJson != null && !rawJson.isBlank()) {
                JsonObject parsed = JsonParser.parseString(rawJson).getAsJsonObject();
                if (parsed.has("object")) {
                    return parsed.get("object").getAsJsonObject();
                }
                // Se não tem "object", o rawJson é o próprio objeto
                return parsed;
            }

            log.warn("[EXTRACTOR] Could not extract raw JSON from event {}", 
                event.getId() != null ? event.getId() : "unknown");
            return null;

        } catch (Exception e) {
            log.error("[EXTRACTOR] Error extracting data object JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extrai String do JSON com segurança
     */
    public static String getString(JsonObject json, String field) {
        if (json == null || !json.has(field)) {
            return null;
        }
        try {
            JsonElement elem = json.get(field);
            if (elem.isJsonNull()) {
                return null;
            }
            return elem.getAsString();
        } catch (Exception e) {
            log.debug("[EXTRACTOR] Error extracting string field '{}': {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * Extrai Long do JSON com segurança
     */
    public static Long getLong(JsonObject json, String field) {
        if (json == null || !json.has(field)) {
            return null;
        }
        try {
            JsonElement elem = json.get(field);
            if (elem.isJsonNull()) {
                return null;
            }
            return elem.getAsLong();
        } catch (Exception e) {
            log.debug("[EXTRACTOR] Error extracting long field '{}': {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * Extrai Boolean do JSON com segurança
     */
    public static Boolean getBoolean(JsonObject json, String field) {
        if (json == null || !json.has(field)) {
            return null;
        }
        try {
            JsonElement elem = json.get(field);
            if (elem.isJsonNull()) {
                return null;
            }
            return elem.getAsBoolean();
        } catch (Exception e) {
            log.debug("[EXTRACTOR] Error extracting boolean field '{}': {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * Extrai JsonObject do JSON com segurança
     */
    public static JsonObject getJsonObject(JsonObject json, String field) {
        if (json == null || !json.has(field)) {
            return null;
        }
        try {
            JsonElement elem = json.get(field);
            if (elem.isJsonNull() || !elem.isJsonObject()) {
                return null;
            }
            return elem.getAsJsonObject();
        } catch (Exception e) {
            log.debug("[EXTRACTOR] Error extracting JsonObject field '{}': {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * Extrai Customer ID do objeto Invoice/Charge/Subscription/Customer
     */
    public static String extractCustomerId(JsonObject dataObject) {
        if (dataObject == null) {
            return null;
        }

        // Para Invoice, Charge, Subscription: têm campo "customer"
        String customerId = getString(dataObject, "customer");
        if (customerId != null && !customerId.isBlank()) {
            return customerId;
        }

        // Para Customer event: ID está em "id"
        customerId = getString(dataObject, "id");
        if (customerId != null && customerId.startsWith("cus_")) {
            return customerId;
        }

        return null;
    }

    /**
     * Extrai Subscription ID do objeto
     */
    public static String extractSubscriptionId(JsonObject dataObject) {
        if (dataObject == null) {
            return null;
        }
        return getString(dataObject, "subscription");
    }

    /**
     * Extrai Invoice ID do objeto
     */
    public static String extractInvoiceId(JsonObject dataObject) {
        if (dataObject == null) {
            return null;
        }
        return getString(dataObject, "id");
    }

    /**
     * Verifica Event Type
     */
    public static String extractEventType(Event event) {
        if (event == null) {
            return null;
        }
        return event.getType();
    }

    /**
     * Verifica Event ID
     */
    public static String extractEventId(Event event) {
        if (event == null) {
            return null;
        }
        return event.getId();
    }
}
