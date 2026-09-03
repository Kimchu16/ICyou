package com.matissjurevics.icyou.lease;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/** Maintains the exact chunk-ticket set needed by retained camera feeds. */
public final class ChunkLeaseManager {

    public record LeaseLocation(RegistryKey<World> dimension, ChunkPos chunk) {
        public LeaseLocation {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(chunk, "chunk");
        }
    }

    public interface TicketSink {
        void acquire(LeaseLocation location);

        void release(LeaseLocation location);
    }

    private final TicketSink tickets;
    private final Map<UUID, Set<LeaseLocation>> leasesByCamera = new LinkedHashMap<>();
    private final Map<LeaseLocation, Integer> referenceCounts = new LinkedHashMap<>();

    public ChunkLeaseManager(TicketSink tickets) {
        this.tickets = Objects.requireNonNull(tickets, "tickets");
    }

    /**
     * Reconciles leases for demanded or retaining cameras. Missing and inactive
     * cameras release their tickets immediately.
     */
    public synchronized void reconcile(Map<UUID, CameraRef> cameras,
                                       Set<UUID> retainedCameraIds) {
        Objects.requireNonNull(cameras, "cameras");
        Objects.requireNonNull(retainedCameraIds, "retainedCameraIds");
        Map<UUID, Set<LeaseLocation>> wanted = new LinkedHashMap<>();
        for (UUID cameraId : retainedCameraIds) {
            CameraRef camera = cameras.get(cameraId);
            if (camera != null) {
                wanted.put(cameraId, area(camera));
            }
        }

        Set<UUID> allCameraIds = new LinkedHashSet<>(leasesByCamera.keySet());
        allCameraIds.addAll(wanted.keySet());
        for (UUID cameraId : allCameraIds) {
            Set<LeaseLocation> current = leasesByCamera.getOrDefault(cameraId, Set.of());
            Set<LeaseLocation> next = wanted.getOrDefault(cameraId, Set.of());
            current.stream().filter(location -> !next.contains(location))
                    .forEach(this::release);
            next.stream().filter(location -> !current.contains(location))
                    .forEach(this::acquire);
            if (next.isEmpty()) {
                leasesByCamera.remove(cameraId);
            } else {
                leasesByCamera.put(cameraId, Set.copyOf(next));
            }
        }
    }

    public synchronized int referenceCount(LeaseLocation location) {
        return referenceCounts.getOrDefault(location, 0);
    }

    public synchronized Set<LeaseLocation> leases(UUID cameraId) {
        return leasesByCamera.getOrDefault(cameraId, Set.of());
    }

    public synchronized void clear() {
        leasesByCamera.forEach((cameraId, locations) ->
                locations.forEach(this::release));
        leasesByCamera.clear();
        referenceCounts.clear();
    }

    private void acquire(LeaseLocation location) {
        int previous = referenceCounts.getOrDefault(location, 0);
        if (previous == 0) {
            tickets.acquire(location);
        }
        referenceCounts.put(location, previous + 1);
    }

    private void release(LeaseLocation location) {
        int count = referenceCounts.getOrDefault(location, 0);
        if (count <= 1) {
            referenceCounts.remove(location);
            tickets.release(location);
        } else {
            referenceCounts.put(location, count - 1);
        }
    }

    private static Set<LeaseLocation> area(CameraRef camera) {
        int radius = (CameraOverhaulContracts.SIMULATED_CHUNK_DIAMETER - 1) / 2;
        ChunkPos center = new ChunkPos(camera.position());
        Set<LeaseLocation> result = new LinkedHashSet<>();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                result.add(new LeaseLocation(camera.dimension(), new ChunkPos(x, z)));
            }
        }
        return Set.copyOf(result);
    }
}
