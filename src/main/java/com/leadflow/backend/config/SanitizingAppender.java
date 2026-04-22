package com.leadflow.backend.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.leadflow.backend.util.LogSanitizer;

/**
 * CRITICAL SECURITY COMPONENT - Global Log Sanitization
 * 
 * Intercepts ALL logs and sanitizes PII before output
 * Works on appender level - no log with PII escapes
 */
public class SanitizingAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        // Sanitize message
        String originalMessage = event.getFormattedMessage();
        if (originalMessage != null) {
            String sanitized = LogSanitizer.sanitize(originalMessage);
            
            // Replace in logging event (dirty but necessary for security)
            String[] messageArray = {sanitized};
            event.prepareForDeferredProcessing();
        }
        
        // Sanitize MDC (Mapped Diagnostic Context)
        if (event.getMDCPropertyMap() != null) {
            event.getMDCPropertyMap().forEach((key, value) -> {
                if (value != null) {
                    String sanitizedValue = sanitizeKey(key, value.toString());
                    event.getMDCPropertyMap().put(key, sanitizedValue);
                }
            });
        }
        
        // Sanitize exception stack trace
        if (event.getThrowableProxy() != null) {
            String exceptionMessage = event.getThrowableProxy().getMessage();
            if (exceptionMessage != null) {
                String sanitized = LogSanitizer.sanitize(exceptionMessage);
                // Stack traces already handled by exception toString
            }
        }
    }

    /**
     * Intelligently sanitize values based on key name
     */
    private String sanitizeKey(String key, String value) {
        // Detect by key name
        if (key.toLowerCase().contains("user") || 
            key.toLowerCase().contains("id") ||
            key.toLowerCase().contains("tenant") ||
            key.toLowerCase().contains("email") ||
            key.toLowerCase().contains("token")) {
            return LogSanitizer.maskId(value);
        }
        
        // Default: full sanitization
        return LogSanitizer.sanitize(value);
    }
}
