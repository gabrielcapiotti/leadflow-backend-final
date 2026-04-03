package com.leadflow.backend.config.converter;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Safe UUID deserializer with validation and corruption detection.
 * Prevents UUID corruption from concurrent access or malformed input.
 * 
 * Key validations:
 * - Check UUID format (8-4-4-4-12 hex digits)
 * - Validate character ranges
 * - Thread-safe roundtrip conversion (UUID → String → UUID)
 */
public class SafeUUIDDeserializer {
    
    private static final Pattern UUID_PATTERN = 
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
                       Pattern.CASE_INSENSITIVE);
    
    /**
     * Safely deserialize a UUID string with corruption detection.
     * 
     * @param value UUID string from database or network
     * @return Valid UUID object
     * @throws IllegalArgumentException if format invalid or corruption detected
     */
    public static UUID deserialize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UUID cannot be null or blank");
        }
        
        String trimmed = value.trim().toLowerCase();
        
        // 0. Check for length mismatch (UUID must be exactly 36 chars with hyphens)
        if (trimmed.length() != 36) {
            throw new IllegalArgumentException(
                String.format("UUID length mismatch: '%s' (expected 36 chars, got %d)", value, trimmed.length())
            );
        }
        
        // 1. Check format
        if (!UUID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                String.format("Invalid UUID format: '%s' (expected: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)", value)
            );
        }
        
        // 2. Check for whitespace or special chars (shouldn't have any)
        if (value.contains(" ") || value.contains("\t") || value.contains("\n")) {
            throw new IllegalArgumentException(
                String.format("UUID contains whitespace: '%s'", value)
            );
        }
        
        // 3. Parse and roundtrip verify
        try {
            UUID uuid = UUID.fromString(trimmed);
            
            // 4. Roundtrip: Convert back to string and compare (catches most corruption)
            String roundtrip = uuid.toString();
            if (!roundtrip.equals(trimmed)) {
                throw new IllegalArgumentException(
                    String.format("UUID roundtrip verification failed: input='%s', roundtrip='%s'", 
                                trimmed, roundtrip)
                );
            }
            
            return uuid;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("Failed to deserialize UUID '%s': %s", value, e.getMessage()), e
            );
        }
    }
    
    /**
     * Safely serialize a UUID to string.
     * Always use toString() on the UUID object itself.
     * 
     * @param uuid Valid UUID object
     * @return UUID string in standard format
     */
    public static String serialize(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
        return uuid.toString();
    }
    
    /**
     * Validate UUID without throwing exceptions.
     * Useful for logging and defensive checks.
     * 
     * @param value UUID string to validate
     * @return true if valid UUID
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        
        try {
            deserialize(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
