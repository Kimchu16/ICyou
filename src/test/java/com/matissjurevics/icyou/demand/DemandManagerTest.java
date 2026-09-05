package com.matissjurevics.icyou.demand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.overhaul.FeedLifecycleState;

class DemandManagerTest {

    private static final DemandManager.ActivationContext ACTIVE =
            new DemandManager.ActivationContext(DemandManager.ServerMode.INTEGRATED,
                    true, false, 1, 0);

    @Test
    void combinesWebAndScreenDemandByCamera() {
        DemandManager manager = new DemandManager();
        UUID camera = UUID.randomUUID();

        manager.reconcile(Map.of(camera, 2), Map.of(camera, 3), ACTIVE, Instant.EPOCH);

        var demand = manager.demand(camera).orElseThrow();
        assertEquals(2, demand.webViewers());
        assertEquals(3, demand.screens());
        assertTrue(demand.demanded());
        assertEquals(FeedLifecycleState.ACTIVATING, demand.lifecycle());
    }

    @Test
    void retainsResourcesForThirtySecondsAfterFinalDemand() {
        DemandManager manager = new DemandManager();
        UUID camera = UUID.randomUUID();
        manager.reconcile(Map.of(camera, 1), Map.of(), ACTIVE, Instant.EPOCH);
        manager.markAvailable(camera);

        manager.reconcile(Map.of(), Map.of(), ACTIVE, Instant.ofEpochSecond(1));
        assertEquals(FeedLifecycleState.RETAINING, manager.lifecycle(camera));
        manager.reconcile(Map.of(), Map.of(), ACTIVE, Instant.ofEpochSecond(30));
        assertEquals(FeedLifecycleState.RETAINING, manager.lifecycle(camera));
        manager.reconcile(Map.of(), Map.of(), ACTIVE, Instant.ofEpochSecond(31));
        assertEquals(FeedLifecycleState.INACTIVE, manager.lifecycle(camera));
    }

    @Test
    void renewedDemandReactivatesDuringRetention() {
        DemandManager manager = new DemandManager();
        UUID camera = UUID.randomUUID();
        manager.reconcile(Map.of(camera, 1), Map.of(), ACTIVE, Instant.EPOCH);
        manager.reconcile(Map.of(), Map.of(), ACTIVE, Instant.ofEpochSecond(1));

        manager.reconcile(Map.of(), Map.of(camera, 1), ACTIVE, Instant.ofEpochSecond(20));

        assertEquals(FeedLifecycleState.ACTIVATING, manager.lifecycle(camera));
    }

    @Test
    void unavailableEnvironmentKeepsDemandButBlocksActivation() {
        DemandManager manager = new DemandManager();
        UUID camera = UUID.randomUUID();
        var paused = new DemandManager.ActivationContext(
                DemandManager.ServerMode.INTEGRATED, true, true, 1, 0);

        manager.reconcile(Map.of(camera, 1), Map.of(), paused, Instant.EPOCH);

        assertEquals(FeedLifecycleState.UNAVAILABLE, manager.lifecycle(camera));
        assertTrue(manager.demand(camera).orElseThrow().demanded());
    }

    @Test
    void environmentRulesCoverIntegratedLanAndDedicatedServers() {
        assertTrue(new DemandManager.ActivationContext(DemandManager.ServerMode.INTEGRATED,
                true, false, 0, 0).permitsActivation());
        assertFalse(new DemandManager.ActivationContext(DemandManager.ServerMode.INTEGRATED,
                true, true, 1, 0).permitsActivation());
        assertFalse(new DemandManager.ActivationContext(DemandManager.ServerMode.LAN,
                true, false, 0, 1).permitsActivation());
        assertTrue(new DemandManager.ActivationContext(DemandManager.ServerMode.LAN,
                true, false, 1, 0).permitsActivation());
        assertFalse(new DemandManager.ActivationContext(DemandManager.ServerMode.DEDICATED,
                true, false, 0, 0).permitsActivation());
        assertTrue(new DemandManager.ActivationContext(DemandManager.ServerMode.DEDICATED,
                true, false, 0, 1).permitsActivation());
    }

    @Test
    void mediaStateRequiresLiveDemand() {
        DemandManager manager = new DemandManager();
        assertThrows(IllegalStateException.class,
                () -> manager.markAvailable(UUID.randomUUID()));
    }

    @Test
    void configurableGracePeriodControlsReleaseTime() {
        DemandManager manager = new DemandManager(Duration.ofSeconds(5));
        UUID camera = UUID.randomUUID();
        manager.reconcile(Map.of(camera, 1), Map.of(), ACTIVE, Instant.EPOCH);
        manager.reconcile(Map.of(), Map.of(), ACTIVE, Instant.ofEpochSecond(1));

        manager.reconcile(Map.of(), Map.of(), ACTIVE, Instant.ofEpochSecond(5));
        assertEquals(FeedLifecycleState.RETAINING, manager.lifecycle(camera));
        manager.reconcile(Map.of(), Map.of(), ACTIVE, Instant.ofEpochSecond(6));
        assertEquals(FeedLifecycleState.INACTIVE, manager.lifecycle(camera));
    }
}
