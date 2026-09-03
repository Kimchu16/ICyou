package com.matissjurevics.icyou.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.demand.DemandManager;
import com.matissjurevics.icyou.overhaul.FeedLifecycleState;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

class ChunkLeaseManagerTest {

    private static final RegistryKey<World> OVERWORLD = World.OVERWORLD;
    private static final RegistryKey<World> OTHER = RegistryKey.of(RegistryKeys.WORLD,
            Identifier.of("icyou", "other"));

    @Test
    void leasesThreeByThreeAreaAroundCameraChunk() {
        RecordingTickets tickets = new RecordingTickets();
        ChunkLeaseManager manager = new ChunkLeaseManager(tickets);
        CameraRef camera = camera(OVERWORLD, 32, 48);

        manager.reconcile(Map.of(camera.deviceId(), camera), Set.of(camera.deviceId()));

        assertEquals(9, manager.leases(camera.deviceId()).size());
        assertTrue(manager.leases(camera.deviceId()).contains(location(OVERWORLD, 1, 2)));
        assertTrue(manager.leases(camera.deviceId()).contains(location(OVERWORLD, 3, 4)));
        assertEquals(9, tickets.acquired.size());
    }

    @Test
    void overlappingCamerasReferenceCountSharedChunks() {
        RecordingTickets tickets = new RecordingTickets();
        ChunkLeaseManager manager = new ChunkLeaseManager(tickets);
        CameraRef first = camera(OVERWORLD, 0, 0);
        CameraRef second = camera(OVERWORLD, 1, 1);
        var center = location(OVERWORLD, 0, 0);

        manager.reconcile(Map.of(first.deviceId(), first, second.deviceId(), second),
                Set.of(first.deviceId(), second.deviceId()));
        assertEquals(2, manager.referenceCount(center));

        manager.reconcile(Map.of(first.deviceId(), first, second.deviceId(), second),
                Set.of(second.deviceId()));
        assertEquals(1, manager.referenceCount(center));

        manager.reconcile(Map.of(), Set.of());
        assertEquals(0, manager.referenceCount(center));
        assertEquals(9, tickets.released.size());
    }

    @Test
    void cameraMoveReplacesOldDimensionAndChunkTickets() {
        RecordingTickets tickets = new RecordingTickets();
        ChunkLeaseManager manager = new ChunkLeaseManager(tickets);
        CameraRef original = camera(OVERWORLD, 0, 0);
        CameraRef moved = new CameraRef(original.deviceId(), OTHER, new BlockPos(160, 0, 160));

        manager.reconcile(Map.of(original.deviceId(), original), Set.of(original.deviceId()));
        manager.reconcile(Map.of(moved.deviceId(), moved), Set.of(moved.deviceId()));

        assertEquals(18, tickets.acquired.size());
        assertEquals(9, tickets.released.size());
        assertTrue(manager.leases(moved.deviceId()).stream()
                .allMatch(location -> location.dimension().equals(OTHER)));
    }

    @Test
    void inactiveOrMissingCamerasReleaseEveryTicket() {
        RecordingTickets tickets = new RecordingTickets();
        ChunkLeaseManager manager = new ChunkLeaseManager(tickets);
        CameraRef camera = camera(OVERWORLD, 0, 0);
        manager.reconcile(Map.of(camera.deviceId(), camera), Set.of(camera.deviceId()));

        manager.reconcile(Map.of(camera.deviceId(), camera), Set.of());

        assertTrue(manager.leases(camera.deviceId()).isEmpty());
        assertEquals(9, tickets.released.size());
    }

    @Test
    void clearReleasesAllOutstandingTickets() {
        RecordingTickets tickets = new RecordingTickets();
        ChunkLeaseManager manager = new ChunkLeaseManager(tickets);
        CameraRef camera = camera(OVERWORLD, 0, 0);
        manager.reconcile(Map.of(camera.deviceId(), camera), Set.of(camera.deviceId()));

        manager.clear();

        assertEquals(9, tickets.released.size());
        assertTrue(manager.leases(camera.deviceId()).isEmpty());
    }

    @Test
    void retainingDemandKeepsTicketsUntilGraceExpires() {
        RecordingTickets tickets = new RecordingTickets();
        ChunkLeaseManager leases = new ChunkLeaseManager(tickets);
        DemandManager demand = new DemandManager();
        CameraRef camera = camera(OVERWORLD, 0, 0);
        var context = new DemandManager.ActivationContext(
                DemandManager.ServerMode.INTEGRATED, true, false, 1, 0);

        demand.reconcile(Map.of(camera.deviceId(), 1), Map.of(), context, Instant.EPOCH);
        sync(leases, demand, camera);
        demand.reconcile(Map.of(), Map.of(), context, Instant.ofEpochSecond(1));
        sync(leases, demand, camera);
        assertEquals(0, tickets.released.size());

        demand.reconcile(Map.of(), Map.of(), context, Instant.ofEpochSecond(31));
        sync(leases, demand, camera);
        assertEquals(9, tickets.released.size());
    }

    private static CameraRef camera(RegistryKey<World> dimension, int x, int z) {
        return new CameraRef(UUID.randomUUID(), dimension, new BlockPos(x, 64, z));
    }

    private static ChunkLeaseManager.LeaseLocation location(RegistryKey<World> dimension,
                                                            int x, int z) {
        return new ChunkLeaseManager.LeaseLocation(dimension, new ChunkPos(x, z));
    }

    private static void sync(ChunkLeaseManager leases, DemandManager demand,
                             CameraRef camera) {
        Set<UUID> retained = demand.demands().entrySet().stream()
                .filter(entry -> entry.getValue().lifecycle() != FeedLifecycleState.INACTIVE)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        leases.reconcile(Map.of(camera.deviceId(), camera), retained);
    }

    private static final class RecordingTickets implements ChunkLeaseManager.TicketSink {
        private final List<String> acquired = new ArrayList<>();
        private final List<String> released = new ArrayList<>();

        @Override
        public void acquire(ChunkLeaseManager.LeaseLocation location) {
            acquired.add(location.toString());
        }

        @Override
        public void release(ChunkLeaseManager.LeaseLocation location) {
            released.add(location.toString());
        }
    }
}
