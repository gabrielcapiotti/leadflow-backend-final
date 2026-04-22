package com.leadflow.backend.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY UTILITY - Log Sanitization
 * 
 * Removes all PII from logs to prevent:
 * - User session reconstruction
 * - Tenant correlation attacks
 * - Email enumeration
 * - Identity leakage
 */
public final class LogSanitizer {

    // JWT/Token patterns
    private static final Pattern BEARER_PATTERN =
            Pattern.compile("Bearer\\s+[A-Za-z0-9-_.]+");
    private static final Pattern PASSWORD_JSON_PATTERN =
            Pattern.compile("(\\\"password\\\"\\s*:\\s*)\\\".*?\\\"");
    private static final Pattern TOKEN_JSON_PATTERN =
            Pattern.compile("(\\\"(?:token|accessToken|refreshToken|jwt|jwtToken)\\\"\\s*:\\s*)\\\".*?\\\"");
    
    // PII patterns
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern EXPLICIT_USER_ID_PATTERN =
            Pattern.compile("(?:user[_.]?id\\s*[=:])\\s*([^,}\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_TENANT_ID_PATTERN =
            Pattern.compile("(?:tenant[_.]?id\\s*[=:])\\s*([^,}\\s]+)", Pattern.CASE_INSENSITIVE);

    private LogSanitizer() {
    }

    /**
     * Full sanitization pipeline: tokens → passwords → PII
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        // Stage 1: Tokens & Secrets
        String sanitized = BEARER_PATTERN.matcher(input)
                .replaceAll("Bearer [REDACTED]");
        sanitized = PASSWORD_JSON_PATTERN.matcher(sanitized)
                .replaceAll("$1\"[REDACTED]\"");
        sanitized = TOKEN_JSON_PATTERN.matcher(sanitized)
                .replaceAll("$1\"[REDACTED]\"");

        // Stage 2: PII - Remove emails (replace with hash)
        sanitized = EMAIL_PATTERN.matcher(sanitized)
                .replaceAll("[EMAIL_REDACTED]");

        // Stage 3: UUIDs (tenant/user IDs)
        sanitized = UUID_PATTERN.matcher(sanitized)
                .replaceAll("[UUID_REDACTED]");

        // Stage 4: Explicit key=value PII
        sanitized = EXPLICIT_USER_ID_PATTERN.matcher(sanitized)
                .replaceAll("user_id=[REDACTED]");
        sanitized = EXPLICIT_TENANT_ID_PATTERN.matcher(sanitized)
                .replaceAll("tenant_id=[REDACTED]");

        return sanitized;
    }

    /**
     * Mask UUID for logging (keep prefix only)
     * Example: "f47ac10b-58cc-4372-a567-0e02b2c3d479" → "f47ac10b-***"
     */
    public static String maskUUID(UUID uuid) {
        if (uuid == null) return "[NULL]";
        String str = uuid.toString();
        return str.substring(0, 8) + "-****";
    }

    /**
     * Mask UUID from String
     */
    public static String maskUUID(String uuid) {
        if (uuid == null || uuid.length() < 8) return "****";
        return uuid.substring(0, Math.min(8, uuid.length())) + "-****";
    }

    /**
     * Mask email: admin@example.com → a***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null) return "[NULL]";
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return "***@***";
        String user = email.substring(0, 1) + "***";
        String domain = email.substring(atIndex + 1);
        return user + "@" + domain;
    }

    /**
     * Mask long ID: keep first 4 and last 2 chars
     */
    public static String maskId(Object id) {
        if (id == null) return "[NULL]";
        String str = id.toString();
        if (str.length() <= 6) return "****";
        return str.substring(0, 4) + "..." + str.substring(str.length() - 2);
    }

    /**
     * Safe logging: Never log these directly - use this instead
     */
    public static String safeUserId() {
        return "[USER_ID]";
    }

    public static String safeTenantId() {
        return "[TENANT_ID]";
    }

    public static String safeEmail() {
        return "[EMAIL]";
    }

    public static String safeToken() {
        return "[TOKEN]";
    }
}
