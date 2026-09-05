package com.matissjurevics.icyou.web.demand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import com.matissjurevics.icyou.web.auth.TerminalCredentialStore.Scope;

import org.junit.jupiter.api.Test;

class WebViewerDemandRegistryTest {

    @Test
    void deduplicatesOneCredentialPerCameraButCountsDifferentViewers() {
        WebViewerDemandRegistry demand = new WebViewerDemandRegistry();
        UUID terminal = UUID.randomUUID();
        UUID camera = UUID.randomUUID();
        UUID firstCredential = UUID.randomUUID();
        Instant start = Instant.EPOCH;

        var first = demand.open(firstCredential, Scope.VIEWER, terminal, camera, start);
        var repeated = demand.open(firstCredential, Scope.VIEWER, terminal, camera,
                start.plusSeconds(1));
        demand.open(UUID.randomUUID(), Scope.VIEWER, terminal, camera, start.plusSeconds(1));

        assertEquals(first.sessionId(), repeated.sessionId());
        assertEquals(2, demand.viewerCount(camera, start.plusSeconds(1)));
        assertTrue(demand.hasDemand(camera, start.plusSeconds(1)));
    }

    @Test
    void renewAndCloseRequireTheExactCredentialTerminalAndCamera() {
        WebViewerDemandRegistry demand = new WebViewerDemandRegistry();
        UUID credential = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        UUID camera = UUID.randomUUID();
        Instant start = Instant.EPOCH;
        var session = demand.open(credential, Scope.VIEWER, terminal, camera, start);

        assertTrue(demand.renew(session.sessionId(), credential, terminal, camera,
                start.plusSeconds(20)).isPresent());
        assertTrue(demand.renew(session.sessionId(), UUID.randomUUID(), terminal, camera,
                start.plusSeconds(21)).isEmpty());
        assertFalse(demand.close(session.sessionId(), credential, terminal, UUID.randomUUID()));
        assertTrue(demand.close(session.sessionId(), credential, terminal, camera));
        assertFalse(demand.hasDemand(camera, start.plusSeconds(21)));
    }

    @Test
    void staleSessionsExpireAndRenewalExtendsTheDeadline() {
        WebViewerDemandRegistry demand = new WebViewerDemandRegistry();
        UUID credential = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        UUID camera = UUID.randomUUID();
        Instant start = Instant.EPOCH;
        var session = demand.open(credential, Scope.OWNER, terminal, camera, start);

        assertEquals(0, demand.expire(start.plusSeconds(29)));
        demand.renew(session.sessionId(), credential, terminal, camera, start.plusSeconds(20));
        assertEquals(0, demand.expire(start.plusSeconds(49)));
        assertEquals(1, demand.expire(start.plusSeconds(50)));
        assertTrue(demand.sessions(start.plusSeconds(50)).isEmpty());
    }

    @Test
    void credentialRevocationDropsDemandImmediately() {
        WebViewerDemandRegistry demand = new WebViewerDemandRegistry();
        UUID terminal = UUID.randomUUID();
        UUID camera = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        demand.open(viewer, Scope.VIEWER, terminal, camera, Instant.EPOCH);
        demand.open(owner, Scope.OWNER, terminal, camera, Instant.EPOCH);

        assertTrue(demand.revokeCredential(viewer));
        assertEquals(1, demand.viewerCount(camera, Instant.EPOCH));
        assertEquals(1, demand.revokeAll(terminal, Scope.OWNER));
        assertFalse(demand.hasDemand(camera, Instant.EPOCH));
    }

    @Test
    void enforcesPerCameraAndTotalViewerLimitsAfterDeduplication() {
        WebViewerDemandRegistry demand = new WebViewerDemandRegistry(1, 2);
        UUID terminal = UUID.randomUUID();
        UUID firstCamera = UUID.randomUUID();
        UUID secondCamera = UUID.randomUUID();
        UUID firstCredential = UUID.randomUUID();

        assertTrue(demand.tryOpen(firstCredential, Scope.VIEWER, terminal,
                firstCamera, Instant.EPOCH).isPresent());
        assertTrue(demand.tryOpen(firstCredential, Scope.VIEWER, terminal,
                firstCamera, Instant.EPOCH.plusSeconds(1)).isPresent());
        assertTrue(demand.tryOpen(UUID.randomUUID(), Scope.VIEWER, terminal,
                firstCamera, Instant.EPOCH).isEmpty());
        assertTrue(demand.tryOpen(UUID.randomUUID(), Scope.VIEWER, terminal,
                secondCamera, Instant.EPOCH).isPresent());
        assertTrue(demand.tryOpen(UUID.randomUUID(), Scope.VIEWER, terminal,
                UUID.randomUUID(), Instant.EPOCH).isEmpty());
    }
}
