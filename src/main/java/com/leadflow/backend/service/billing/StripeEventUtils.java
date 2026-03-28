package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.model.Event;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for extracting and parsing Stripe webhook event data.
 * 
 * This centralizes the correct pattern for accessing Stripe webhook JSON:
 * - Extract raw JSON from event
 * - Parse to JsonObject
 * - Access data.object structure
 * - Extract required fields safely
 * 
 * Prevents errors like: "Not a JSON Object: invoice"
 * which occur when malformed payloads have object as string instead of JSON object.
 */
@UtilityClass
@Slf4j
public class StripeEventUtils {
    
    /**
     * Extract the object data from a Stripe webhook event.
     * 
     * Stripe webhook structure (always):
     * {
     *   "data": {
     *     "object": { ... }
     *   }
     * }
     * 
     * @param event The Stripe event
     * @return The object JsonObject containing event data
     * @throws IllegalStateException if structure is invalid or object is not a JSON object
     */
    public static JsonObject getObject(Event event) {
        if (event == null) {
            throw new IllegalStateException("Event cannot be null");
        }
        
        String rawJson = event.getDataObjectDeserializer().getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("Event has no raw JSON data");
        }
        
        JsonObject dataObject = JsonParser.parseString(rawJson).getAsJsonObject();
        if (!dataObject.has("object") || dataObject.get("object").isJsonNull()) {
            throw new IllegalStateException("Event data has no 'object' field");
        }
        
        // This will throw IllegalStateException: "Not a JSON Object: ..." if object is not JSON
        // (e.g., if it's a string like "invoice")
         return dataObject.get("object").getAsJsonObject();
    }
    
    /**
     * Safely extract a string field from a JSON object.
     * Returns null if field missing, null, or not a string.
     */
    public static String extractString(JsonObject json, String fieldName) {
        try {
            if (json.has(fieldName) && !json.get(fieldName).isJsonNull()) {
                String value = json.get(fieldName).getAsString();
                return (value != null && !value.isBlank()) ? value : null;
            }
        } catch (Exception e) {
            log.warn("Error extracting string field '{}': {}", fieldName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Safely extract a long field from a JSON object.
     * Returns 0 if field missing, null, or not a number.
     */
    public static long extractLong(JsonObject json, String fieldName) {
        try {
            if (json.has(fieldName) && !json.get(fieldName).isJsonNull()) {
                return json.get(fieldName).getAsLong();
            }
        } catch (Exception e) {
            log.warn("Error extracting long field '{}': {}", fieldName, e.getMessage());
        }
        return 0L;
    }
    
    /**
     * Safely extract an integer field from a JSON object.
     * Returns 0 if field missing, null, or not a number.
     */
    public static int extractInt(JsonObject json, String fieldName) {
        try {
            if (json.has(fieldName) && !json.get(fieldName).isJsonNull()) {
                return json.get(fieldName).getAsInt();
            }
        } catch (Exception e) {
            log.warn("Error extracting int field '{}': {}", fieldName, e.getMessage());
        }
        return 0;
    }
}
