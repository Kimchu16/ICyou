package com.matissjurevics.icyou.lease;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import com.matissjurevics.icyou.demand.ServerDemandLifecycle;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.overhaul.FeedLifecycleState;
import com.matissjurevics.icyou.admin.ServerAdminLimitsLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ChunkLevels;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/** Adapts retained camera demand to Minecraft chunk tickets. */
public final class ServerChunkLeaseLifecycle {

    private static final ChunkTicketType<ChunkPos> CAMERA_TICKET =
            ChunkTicketType.create("icyou_camera",
                    Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_LEVEL = ChunkLevels.getLevelFromType(
            ChunkLevelType.ENTITY_TICKING);
    private static final Map<MinecraftServer, ChunkLeaseManager> ACTIVE =
            new IdentityHashMap<>();

    private ServerChunkLeaseLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerChunkLeaseLifecycle.class) {
                ACTIVE.putIfAbsent(server, new ChunkLeaseManager(new MinecraftTickets(server),
                        ServerAdminLimitsLifecycle.limits(server)
                                .simulatedChunkDiameter()));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerChunkLeaseLifecycle::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerChunkLeaseLifecycle.class) {
                ChunkLeaseManager leases = ACTIVE.remove(server);
                if (leases != null) {
                    leases.clear();
                }
            }
        });
    }

    public static synchronized Optional<ChunkLeaseManager> leases(MinecraftServer server) {
        return Optional.ofNullable(ACTIVE.get(server));
    }

    private static void tick(MinecraftServer server) {
        ChunkLeaseManager leases;
        synchronized (ServerChunkLeaseLifecycle.class) {
            leases = ACTIVE.get(server);
        }
        if (leases == null) {
            return;
        }
        ServerDemandLifecycle.demand(server).ifPresent(demand -> {
            Set<UUID> retained = demand.demands().entrySet().stream()
                    .filter(entry -> entry.getValue().lifecycle()
                            != FeedLifecycleState.INACTIVE)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            leases.reconcile(cameraRefs(server), retained);
        });
    }

    private static Map<UUID, CameraRef> cameraRefs(MinecraftServer server) {
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(server);
        Map<UUID, CameraRef> cameras = new LinkedHashMap<>();
        registry.terminalIds().stream().flatMap(id -> registry.camerasFor(id).stream())
                .forEach(camera -> cameras.put(camera.ref().deviceId(), camera.ref()));
        return cameras;
    }

    private record MinecraftTickets(MinecraftServer server)
            implements ChunkLeaseManager.TicketSink {

        @Override
        public void acquire(ChunkLeaseManager.LeaseLocation location) {
            ServerWorld world = server.getWorld(location.dimension());
            if (world != null) {
                world.getChunkManager().addTicket(CAMERA_TICKET, location.chunk(),
                        TICKET_LEVEL, location.chunk());
            }
        }

        @Override
        public void release(ChunkLeaseManager.LeaseLocation location) {
            ServerWorld world = server.getWorld(location.dimension());
            if (world != null) {
                world.getChunkManager().removeTicket(CAMERA_TICKET, location.chunk(),
                        TICKET_LEVEL, location.chunk());
            }
        }
    }
}
