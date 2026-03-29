package com.leadflow.backend.service.auth;

import com.leadflow.backend.dto.auth.SessionResponse;
import com.leadflow.backend.entities.auth.UserSession;
import com.leadflow.backend.repository.auth.UserSessionRepository;
import com.leadflow.backend.security.exception.UnauthorizedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserSessionService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);

    private final UserSessionRepository repository;
    private final Clock clock;
    private final int maxDevices;
    private final int idleTimeoutMinutes;
    @Value("${security.session.cleanup-days:30}")
    private int cleanupDays;

    public UserSessionService(
            UserSessionRepository repository,
            Clock clock,
            @Value("${security.session.max-devices:10}") int maxDevices,
            @Value("${security.session.idle-timeout-minutes:30}") int idleTimeoutMinutes
    ) {
        this.repository = repository;
        this.clock = clock;
        this.maxDevices = Math.max(maxDevices, 1);
        this.idleTimeoutMinutes = Math.max(idleTimeoutMinutes, 1);
    }

    /* ======================================================
       TENANT ID CONVERSION HELPER
       ====================================================== */

    private UUID toTenantUUID(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid tenantId (must be UUID): " + tenantId);
        }
    }

    /* ======================================================
       DEVICE LIMIT ENFORCEMENT
       ====================================================== */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void enforceDeviceLimit(UUID userId, String tenantId) {

        UUID tenantUuid = toTenantUUID(tenantId);

        long activeCount =
                repository.countByUserIdAndTenantIdAndActiveTrue(userId, tenantUuid);

        if (activeCount < maxDevices) {
            return;
        }

        List<UserSession> activeSessions =
                repository.findByUserIdAndTenantIdAndActiveTrueOrderByCreatedAtAsc(
                        userId,
                        tenantUuid
                );

        int sessionsToRemove = (int) (activeCount - maxDevices + 1);
        Instant now = Instant.now(clock);

        for (int i = 0; i < sessionsToRemove && i < activeSessions.size(); i++) {
            // ✅ CRITICAL: Delete old sessions completely to free up tokenId
            // This prevents "unique constraint violation" on tokenId when creating new sessions
            UUID sessionToDelete = activeSessions.get(i).getId();
            repository.deleteById(sessionToDelete);
            repository.flush(); // 🔥 ESSENTIAL: Force flush to release constraint immediately
            log.debug("✓ Old session deleted and removed from DB: {}", sessionToDelete);
        }
    }

    /* ======================================================
       CREATE SESSION
       ====================================================== */

    @Transactional
    public void createSession(UUID userId,
                              String tenantId,
                              String tokenId,
                              String ipAddress,
                              String userAgent) {

        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(tokenId, "tokenId cannot be null");

        if (tokenId.isBlank()) {
            throw new UnauthorizedException("tokenId cannot be blank");
        }

        log.info("🔐 CREATE SESSION: userId={}, tenantId={}, tokenId={}", userId, tenantId, tokenId);

        UUID tenantUuid = toTenantUUID(tenantId);
        enforceDeviceLimit(userId, tenantId);

        Instant now = Instant.now(clock);

        UserSession session =
                new UserSession(
                        userId,
                        tenantUuid,
                        tokenId,
                        ipAddress,
                        userAgent,
                        now
                );

        repository.saveAndFlush(session);
        
        log.info("SESSION PERSISTED & FLUSHED: sessionId={}, tokenId={}, user={}, tenant={}", 
            session.getId(), tokenId, userId, tenantId);
    }

    /* ======================================================
       PROCESS SESSION ACTIVITY (SECURITY CORE)
       ====================================================== */

    @Transactional
    public void processSessionActivity(String tokenId,
                                       String tenantId,
                                       String currentIp,
                                       String currentUserAgent) {

        log.debug("🔍 PROCESSING SESSION ACTIVITY: tokenId={}, tenantId={}", tokenId, tenantId);

        UUID tenantUuid = toTenantUUID(tenantId);

        // ✅ IDEMPOTENT: Treat missing session as already logged out
        UserSession session = repository
                .findByTokenIdAndTenantIdAndActiveTrue(tokenId, tenantUuid)
                .orElse(null);
        
        if (session == null) {
            log.warn(
                "Session not found during activity processing (treating as already logged out): tokenId={}, tenantId={}",
                tokenId, tenantId
            );
            return;  // ✅ Silently succeed - session already revoked or expired
        }

        log.debug("✓ Session found: userId={}, sessionId={}", session.getUserId(), session.getId());

        Instant now = Instant.now(clock);

        /* -------- Idle Expiration -------- */

        Instant lastAccess = session.getLastAccessAt();

        if (lastAccess != null) {
            Instant expiration =
                    lastAccess.plusSeconds(idleTimeoutMinutes * 60L);

            if (now.isAfter(expiration)) {
                session.revoke(now);
                throw new UnauthorizedException("Session expired due to inactivity");
            }
        }

        /* -------- Suspicious Detection (User-Agent only - LOGGED ONLY) --------
           Note: User-Agent is logged as suspicious but NOT revoked because:
           - Browsers can change User-Agent header (dev tools, proxies)
           - Postman/scripts explicitly change it
           - Load balancers may modify headers
           - Mobile networks can have different user-agents
           
           If User-Agent truly changes, admin can manually revoke session.
         */

        boolean agentChanged =
                session.getInitialUserAgent() != null &&
                !session.getInitialUserAgent().equals(currentUserAgent);

        if (agentChanged) {
            session.markSuspicious();
            log.warn("User-Agent changed for session {}: {} → {}", 
                session.getId(),
                session.getInitialUserAgent(),
                currentUserAgent);
            // Note: NOT revoking session on User-Agent change (too fragile)
            // Admin can manually revoke if suspicious
        }

        /* -------- Update Activity -------- */

        session.updateDeviceInfo(currentIp, currentUserAgent);
        session.updateActivity(now);
        
        /* -------- Persist Changes -------- */
        repository.saveAndFlush(session);
    }

    /* ======================================================
       REVOKE CURRENT SESSION
       ====================================================== */

    @Transactional
    public void revokeSession(String tokenId, String tenantId) {

        if (tokenId == null || tokenId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid session identifier"
            );
        }

        UUID tenantUuid = toTenantUUID(tenantId);

        // ✅ IDEMPOTENT: Only revoke if session is active, silently succeed if not found or already revoked
        repository
                .findByTokenIdAndTenantIdAndActiveTrue(tokenId, tenantUuid)
                .ifPresent(session -> {
                    session.revoke(Instant.now(clock));
                    log.debug("✓ Session revoked: {}", session.getId());
                });
    }

    /* ======================================================
       REVOKE ALL USER SESSIONS
       ====================================================== */

    @Transactional
    public void revokeAllUserSessions(UUID userId, String tenantId) {

        repository.revokeAllActiveSessions(
                userId,
                toTenantUUID(tenantId),
                Instant.now(clock)
        );
    }

    /* ======================================================
       VALIDATE SESSION
       ====================================================== */

    @Transactional(readOnly = true)
    public void validateActiveSession(String tokenId, String tenantId) {

        boolean exists =
                repository.existsByTokenIdAndTenantIdAndActiveTrue(
                        tokenId,
                        toTenantUUID(tenantId)
                );

        if (!exists) {
            throw new UnauthorizedException("Session revoked or invalid");
        }
    }

    /* ======================================================
       GET ACTIVE SESSIONS FOR USER
       ====================================================== */

    @Transactional(readOnly = true)
    public List<UserSession> getActiveSessionsForUser(UUID userId, String tenantId) {

        return repository.findByUserIdAndTenantIdAndActiveTrueOrderByCreatedAtDesc(
                userId,
                toTenantUUID(tenantId)
        );
    }

    /* ======================================================
       LIST ACTIVE SESSIONS
       ====================================================== */

    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveSessions(UUID userId,
                                                    String tenantId,
                                                    String currentTokenId) {

        return repository
                .findByUserIdAndTenantIdAndActiveTrueOrderByCreatedAtDesc(
                        userId,
                        toTenantUUID(tenantId)
                )
                .stream()
                .map(session -> new SessionResponse(
                        session.getId(),
                        session.getIpAddress() != null ? session.getIpAddress() : "unknown",
                        session.getUserAgent() != null ? session.getUserAgent() : "unknown",
                        session.getCreatedAt(),
                        currentTokenId != null &&
                                currentTokenId.equals(session.getTokenId())
                ))
                .toList();
    }

    /* ======================================================
       REVOKE SPECIFIC SESSION
       ====================================================== */

    @Transactional
    public void revokeSpecificSession(UUID sessionId,
                                      UUID userId,
                                      String tenantId,
                                      String currentTokenId) {

        UserSession session = repository
                .findByIdAndUserIdAndTenantIdAndActiveTrue(
                        sessionId,
                        userId,
                        toTenantUUID(tenantId)
                )
                .orElse(null);
        
        if (session == null) {
            log.warn("Attempt to revoke non-existent or already-revoked session: sessionId={}, userId={}", 
                sessionId, userId);
            return;  // ✅ IDEMPOTENT: Silently succeed
        }

        // Prevent revoking the current session
        if (session.getTokenId().equals(currentTokenId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot revoke current session"
            );
        }

        session.revoke(Instant.now(clock));
        repository.saveAndFlush(session);
    }

    /* ======================================================
       UPDATE SESSION AFTER TOKEN REFRESH
       ====================================================== */

    @Transactional
    public void updateSessionTokenIdAfterRefresh(String oldTokenId,
                                                  String newTokenId,
                                                  String tenantId) {
        log.info("🔄 UPDATING SESSION AFTER REFRESH: oldTokenId={}, newTokenId={}, tenantId={}", 
            oldTokenId, newTokenId, tenantId);
        
        UUID tenantUuid = toTenantUUID(tenantId);
        
        UserSession session = repository
                .findByTokenIdAndTenantIdAndActiveTrue(oldTokenId, tenantUuid)
                .orElse(null);
        
        if (session == null) {
            log.warn("Session not found for refresh (treating as already expired): oldTokenId={}, tenantId={}", 
                oldTokenId, tenantId);
            return;  // ✅ IDEMPOTENT: Session already expired/revoked
        }

        log.debug("✓ Session found: sessionId={}, userId={}", 
            session.getId(), session.getUserId());
        
        // Update the session with the new token ID
        session.setTokenId(newTokenId);
        repository.save(session);
        
        log.info("✓ Session updated with new tokenId: {}", newTokenId);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupRevokedSessions() {
        Instant threshold = Instant.now(clock).minusSeconds(cleanupDays * 24L * 60 * 60);
        repository.deleteRevokedBefore(threshold);
    }
}