package com.leadflow.backend.service.auth;

import com.leadflow.backend.entities.auth.RefreshToken;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.repository.auth.RefreshTokenRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int REFRESH_DAYS = 7;

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /* ======================================================
       GENERATE NEW REFRESH TOKEN (WITH DEVICE BINDING)
       ====================================================== */

    @Transactional
    public String generate(User user,
                           String ipAddress,
                           String userAgent) {

        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Invalid user");
        }

        String rawToken = generateSecureToken();
        String tokenHash = hash(rawToken);

        String fingerprint = generateFingerprint(ipAddress, userAgent);

        RefreshToken entity = new RefreshToken(
                tokenHash,
                fingerprint,
                user,
                LocalDateTime.now().plusDays(REFRESH_DAYS)
        );

        repository.save(entity);

        return rawToken;
    }

    /* ======================================================
       VALIDATE + ROTATE (SECURE ROTATION + DEVICE CHECK)
       ====================================================== */

    @Transactional
    public RotationResult validateAndRotate(String rawToken,
                                            String ipAddress,
                                            String userAgent) {

        log.info("=== REFRESH TOKEN VALIDATION START ===");
        log.debug("IP: {}, UserAgent: {}", ipAddress, userAgent);

        if (rawToken == null || rawToken.isBlank()) {
            log.error("❌ Token is null or blank");
            throw new IllegalArgumentException("Invalid refresh token");
        }

        log.debug("✓ Token is not blank (length: {})", rawToken.length());

        String tokenHash = hash(rawToken);
        log.debug("✓ Token hashed successfully");

        RefreshToken token = repository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.error("❌ Token not found in repository: {}", tokenHash);
                    return new IllegalArgumentException("Invalid refresh token");
                });

        log.info("✓ Token found in repository");

        // 🔒 REUSE DETECTION
        if (token.isRevoked()) {
            log.error("❌ Token is revoked - REUSE DETECTED");
            repository.deleteByUser_Id(token.getUser().getId());
            throw new IllegalStateException("Refresh token reuse detected");
        }

        log.debug("✓ Token is not revoked");

        if (token.isExpired()) {
            log.error("❌ Token is expired");
            token.revoke();
            repository.save(token);
            throw new IllegalArgumentException("Refresh token expired");
        }

        log.debug("✓ Token is not expired");

        // 🔐 DEVICE BINDING VALIDATION
        String currentFingerprint = generateFingerprint(ipAddress, userAgent);
        String storedFingerprint = token.getDeviceFingerprint();

        log.debug("Current fingerprint: {}", currentFingerprint);
        log.debug("Stored fingerprint:  {}", storedFingerprint);

        if (!storedFingerprint.equals(currentFingerprint)) {
            log.error("❌ Device fingerprint mismatch - DEVICE CHANGE DETECTED");
            repository.deleteByUser_Id(token.getUser().getId());
            throw new IllegalStateException("Device mismatch detected");
        }

        log.debug("✓ Device fingerprint matches");

        // 🔁 ROTATION
        log.debug("Revoking old token...");
        token.revoke();
        repository.save(token);
        log.debug("✓ Old token revoked");

        User user = token.getUser();
        log.debug("✓ User retrieved: {}", user.getEmail());

        log.debug("Generating new refresh token...");
        String newRefresh =
                generate(user, ipAddress, userAgent);

        log.info("=== REFRESH TOKEN ROTATION SUCCESSFUL ===");
        return new RotationResult(user, newRefresh);
    }

    /* ======================================================
       CLEANUP
       ====================================================== */

    @Transactional
    public void cleanupExpiredTokens() {
        repository.deleteByExpiresAtBefore(LocalDateTime.now());
        repository.deleteByRevokedTrue();
    }

    /* ======================================================
       INTERNAL UTILITIES
       ====================================================== */

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Token hashing failed", e);
        }
    }

    private String generateFingerprint(String ipAddress,
                                       String userAgent) {

        String raw = (ipAddress == null ? "" : ipAddress) +
                     "|" +
                     (userAgent == null ? "" : userAgent);

        return hash(raw);
    }

    /* ======================================================
       RESULT WRAPPER
       ====================================================== */

    public record RotationResult(User user,
                                 String newRefreshToken) {}
}