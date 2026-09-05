package com.matissjurevics.icyou.web.demand;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.web.auth.TerminalCredentialStore.Scope;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

/** Transient authenticated web-viewer demand, keyed by stable camera UUID. */
public final class WebViewerDemandRegistry {

    public static final Duration SESSION_TIMEOUT = Duration.ofSeconds(30);

    public record ViewerSession(UUID sessionId, UUID credentialId, Scope credentialScope,
                                UUID terminalId,
                                UUID cameraId, Instant lastSeen) {
        public ViewerSession {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(credentialScope, "credentialScope");
            Objects.requireNonNull(terminalId, "terminalId");
            Objects.requireNonNull(cameraId, "cameraId");
            Objects.requireNonNull(lastSeen, "lastSeen");
        }
    }

    private record ViewerKey(UUID credentialId, UUID cameraId) {
    }

    private final Map<UUID, ViewerSession> sessions = new LinkedHashMap<>();
    private final Map<ViewerKey, UUID> sessionByViewer = new LinkedHashMap<>();
    private final int viewersPerCamera;
    private final int totalViewers;

    public WebViewerDemandRegistry() {
        this(CameraOverhaulContracts.MAX_VIEWERS_PER_CAMERA,
                CameraOverhaulContracts.MAX_TOTAL_VIEWERS);
    }

    public WebViewerDemandRegistry(int viewersPerCamera, int totalViewers) {
        if (viewersPerCamera < 1 || totalViewers < viewersPerCamera) {
            throw new IllegalArgumentException("Invalid web viewer limits");
        }
        this.viewersPerCamera = viewersPerCamera;
        this.totalViewers = totalViewers;
    }

    public synchronized ViewerSession open(UUID credentialId, Scope credentialScope,
                                           UUID terminalId,
                                           UUID cameraId, Instant now) {
        return tryOpen(credentialId, credentialScope, terminalId, cameraId, now)
                .orElseThrow(() -> new IllegalStateException("Web viewer limit reached"));
    }

    public synchronized Optional<ViewerSession> tryOpen(UUID credentialId,
            Scope credentialScope, UUID terminalId, UUID cameraId, Instant now) {
        Objects.requireNonNull(now, "now");
        expire(now);
        ViewerKey key = new ViewerKey(credentialId, cameraId);
        UUID existingId = sessionByViewer.get(key);
        if (existingId != null) {
            ViewerSession current = sessions.get(existingId);
            ViewerSession renewed = new ViewerSession(current.sessionId(), credentialId,
                    credentialScope,
                    terminalId, cameraId, now);
            sessions.put(existingId, renewed);
            return Optional.of(renewed);
        }
        long cameraViewers = sessions.values().stream()
                .filter(session -> session.cameraId().equals(cameraId)).count();
        if (sessions.size() >= totalViewers || cameraViewers >= viewersPerCamera) {
            return Optional.empty();
        }
        ViewerSession created = new ViewerSession(UUID.randomUUID(), credentialId,
                credentialScope,
                terminalId, cameraId, now);
        sessions.put(created.sessionId(), created);
        sessionByViewer.put(key, created.sessionId());
        return Optional.of(created);
    }

    public synchronized Optional<ViewerSession> renew(UUID sessionId, UUID credentialId,
                                                      UUID terminalId, UUID cameraId,
                                                      Instant now) {
        expire(now);
        ViewerSession current = sessions.get(sessionId);
        if (current == null || !current.credentialId().equals(credentialId)
                || !current.terminalId().equals(terminalId)
                || !current.cameraId().equals(cameraId)) {
            return Optional.empty();
        }
        ViewerSession renewed = new ViewerSession(current.sessionId(), current.credentialId(),
                current.credentialScope(),
                current.terminalId(), current.cameraId(), now);
        sessions.put(sessionId, renewed);
        return Optional.of(renewed);
    }

    public synchronized boolean close(UUID sessionId, UUID credentialId,
                                      UUID terminalId, UUID cameraId) {
        ViewerSession current = sessions.get(sessionId);
        if (current == null || !current.credentialId().equals(credentialId)
                || !current.terminalId().equals(terminalId)
                || !current.cameraId().equals(cameraId)) {
            return false;
        }
        remove(current);
        return true;
    }

    public synchronized boolean hasDemand(UUID cameraId, Instant now) {
        expire(now);
        return sessions.values().stream().anyMatch(session -> session.cameraId().equals(cameraId));
    }

    public synchronized int viewerCount(UUID cameraId, Instant now) {
        expire(now);
        return (int) sessions.values().stream()
                .filter(session -> session.cameraId().equals(cameraId)).count();
    }

    public synchronized List<ViewerSession> sessions(Instant now) {
        expire(now);
        return List.copyOf(sessions.values());
    }

    public synchronized int expire(Instant now) {
        Instant cutoff = Objects.requireNonNull(now, "now").minus(SESSION_TIMEOUT);
        List<ViewerSession> expired = sessions.values().stream()
                .filter(session -> !session.lastSeen().isAfter(cutoff)).toList();
        expired.forEach(this::remove);
        return expired.size();
    }

    public synchronized boolean revokeCredential(UUID credentialId) {
        List<ViewerSession> revoked = sessions.values().stream()
                .filter(session -> session.credentialId().equals(credentialId)).toList();
        revoked.forEach(this::remove);
        return !revoked.isEmpty();
    }

    public synchronized int revokeAll(UUID terminalId, Scope scope) {
        List<ViewerSession> revoked = sessions.values().stream()
                .filter(session -> session.terminalId().equals(terminalId)
                        && session.credentialScope() == scope).toList();
        revoked.forEach(this::remove);
        return revoked.size();
    }

    public synchronized void clear() {
        sessions.clear();
        sessionByViewer.clear();
    }

    private void remove(ViewerSession session) {
        sessions.remove(session.sessionId());
        sessionByViewer.remove(new ViewerKey(session.credentialId(), session.cameraId()));
    }
}
