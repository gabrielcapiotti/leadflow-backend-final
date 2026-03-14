package com.leadflow.backend.service.auth;

import com.leadflow.backend.dto.auth.SessionResponse;
import com.leadflow.backend.entities.auth.UserSession;
import com.leadflow.backend.repository.auth.UserSessionRepository;
import com.leadflow.backend.security.exception.UnauthorizedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserSessionService {

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
       DEVICE LIMIT ENFORCEMENT
       ====================================================== */

    private void enforceDeviceLimit(UUID userId, UUID tenantId) {

        long activeCount =
                repository.countByUserIdAndTenantIdAndActiveTrue(userId, tenantId);

        if (activeCount < maxDevices) {
            return;
        }

        List<UserSession> activeSessions =
                repository.findByUserIdAndTenantIdAndActiveTrueOrderByCreatedAtAsc(
                        userId,
                        tenantId
                );

        int sessionsToRemove = (int) (activeCount - maxDevices + 1);
        Instant now = Instant.now(clock);

        for (int i = 0; i < sessionsToRemove && i < activeSessions.size(); i++) {
            activeSessions.get(i).revoke(now);
        }
    }

    /* ======================================================
       CREATE SESSION
       ====================================================== */

    @Transactional
    public void createSession(UUID userId,
                              UUID tenantId,
                              String tokenId,
                              String ipAddress,
                              String userAgent) {

        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(tokenId, "tokenId cannot be null");

        if (tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId cannot be blank");
        }

        enforceDeviceLimit(userId, tenantId);

        Instant now = Instant.now(clock);

        UserSession session =
                new UserSession(
                        userId,
                        tenantId,
                        tokenId,
                        ipAddress,
                        userAgent,
                        now
                );

        repository.save(session);
    }

    /* ======================================================
       PROCESS SESSION ACTIVITY (SECURITY CORE)
       ====================================================== */

    @Transactional
    public void processSessionActivity(String tokenId,
                                       UUID tenantId,
                                       String currentIp,
                                       String currentUserAgent) {

        UserSession session = repository
                .findByTokenIdAndTenantIdAndActiveTrue(tokenId, tenantId)
                .orElseThrow(() ->
                        new UnauthorizedException("Session not found"));

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

        /* -------- Suspicious Detection (User-Agent only) --------
           Note: IP-based detection is disabled because:
           - Cloudflare / proxies change the IP per request
           - Mobile networks (4G/5G) switch between towers
           - Load balancers may mask the real IP
           
           Using User-Agent is more reliable for device changes.
         */

        boolean agentChanged =
                session.getInitialUserAgent() != null &&
                !session.getInitialUserAgent().equals(currentUserAgent);

        if (agentChanged) {
            session.markSuspicious();
            session.revoke(now);
            throw new UnauthorizedException("Suspicious session detected: device changed");
        }

        /* -------- Update Activity -------- */

        session.updateDeviceInfo(currentIp, currentUserAgent);
        session.updateActivity(now);
    }

    /* ======================================================
       REVOKE CURRENT SESSION
       ====================================================== */

    @Transactional
    public void revokeSession(String tokenId, UUID tenantId) {

        if (tokenId == null || tokenId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid session identifier"
            );
        }

        UserSession session = repository
                .findByTokenIdAndTenantIdAndActiveTrue(tokenId, tenantId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active session not found"
                        ));

        session.revoke(Instant.now(clock));
    }

    /* ======================================================
       REVOKE ALL USER SESSIONS
       ====================================================== */

    @Transactional
    public void revokeAllUserSessions(UUID userId, UUID tenantId) {

        repository.revokeAllActiveSessions(
                userId,
                tenantId,
                Instant.now(clock)
        );
    }

    /* ======================================================
       VALIDATE SESSION
       ====================================================== */

    @Transactional(readOnly = true)
    public void validateActiveSession(String tokenId, UUID tenantId) {

        boolean exists =
                repository.existsByTokenIdAndTenantIdAndActiveTrue(tokenId, tenantId);

        if (!exists) {
            throw new UnauthorizedException("Session revoked or invalid");
        }
    }

    /* ======================================================
       LIST ACTIVE SESSIONS
       ====================================================== */

    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveSessions(UUID userId,
                                                    UUID tenantId,
                                                    String currentTokenId) {

        return repository
                .findByUserIdAndTenantIdAndActiveTrueOrderByCreatedAtDesc(
                        userId,
                        tenantId
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
                                      UUID tenantId,
                                      String currentTokenId) {

        UserSession session = repository
                .findByIdAndUserIdAndTenantIdAndActiveTrue(
                        sessionId,
                        userId,
                        tenantId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Session not found"
                        ));

        // Prevent revoking the current session
        if (session.getTokenId().equals(currentTokenId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot revoke current session"
            );
        }

        session.revoke(Instant.now(clock));
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupRevokedSessions() {
        Instant threshold = Instant.now(clock).minusSeconds(cleanupDays * 24L * 60 * 60);
        repository.deleteRevokedBefore(threshold);
    }
}