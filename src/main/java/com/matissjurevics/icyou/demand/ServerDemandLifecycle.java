package com.matissjurevics.icyou.demand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;
import com.matissjurevics.icyou.web.ServerWebLifecycle;
import com.matissjurevics.icyou.admin.ServerAdminLimitsLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/** Owns one unified demand manager for each logical server. */
public final class ServerDemandLifecycle {

    private static final Map<MinecraftServer, DemandManager> ACTIVE = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Predicate<UUID>> RENDER_AGENTS =
            new IdentityHashMap<>();

    private ServerDemandLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerDemandLifecycle.class) {
                ACTIVE.putIfAbsent(server, new DemandManager(
                        ServerAdminLimitsLifecycle.limits(server).resourceGracePeriod()));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerDemandLifecycle::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerDemandLifecycle.class) {
                DemandManager demand = ACTIVE.remove(server);
                RENDER_AGENTS.remove(server);
                if (demand != null) {
                    demand.clear();
                }
            }
        });
    }

    public static synchronized Optional<DemandManager> demand(MinecraftServer server) {
        return Optional.ofNullable(ACTIVE.get(server));
    }

    /** Installed by render authentication once PR 14 can identify authorized agents. */
    public static synchronized void setRenderAgentPredicate(MinecraftServer server,
                                                            Predicate<UUID> predicate) {
        RENDER_AGENTS.put(Objects.requireNonNull(server, "server"),
                Objects.requireNonNull(predicate, "predicate"));
    }

    private static void tick(MinecraftServer server) {
        DemandManager manager;
        synchronized (ServerDemandLifecycle.class) {
            manager = ACTIVE.get(server);
        }
        if (manager == null) {
            return;
        }
        Instant now = Instant.now();
        Presence presence = presence(server);
        manager.reconcile(webDemand(server, now), screenDemand(server, presence.players()),
                context(server, presence), now);
    }

    private static Map<UUID, Integer> webDemand(MinecraftServer server, Instant now) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        ServerWebLifecycle.demand(server).ifPresent(demand -> demand.sessions(now)
                .forEach(session -> counts.merge(session.cameraId(), 1, Integer::sum)));
        return counts;
    }

    private static Map<UUID, Integer> screenDemand(
            MinecraftServer server, List<ScreenDemandEvaluator.PlayerView> players) {
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(server);
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        registry.terminalIds().stream().flatMap(id -> registry.screensFor(id).stream())
                .filter(screen -> screen.assignedCameraId().isPresent())
                .forEach(screen -> {
                    ServerWorld world = server.getWorld(screen.ref().dimension());
                    if (world == null) {
                        return;
                    }
                    long chunk = ChunkPos.toLong(screen.ref().position().getX() >> 4,
                            screen.ref().position().getZ() >> 4);
                    boolean loaded = world.isChunkLoaded(chunk)
                            && world.getBlockEntity(screen.ref().position())
                                    instanceof ScreenBlockEntity;
                    var view = new ScreenDemandEvaluator.ScreenView(
                            screen.assignedCameraId().orElseThrow(), screen.ref().dimension(),
                            screen.ref().position(), loaded);
                    if (ScreenDemandEvaluator.hasDemand(view, players)) {
                        counts.merge(view.cameraId(), 1, Integer::sum);
                    }
                });
        return counts;
    }

    private static Presence presence(MinecraftServer server) {
        List<ScreenDemandEvaluator.PlayerView> players = new ArrayList<>();
        Predicate<UUID> renderAgents;
        synchronized (ServerDemandLifecycle.class) {
            renderAgents = RENDER_AGENTS.getOrDefault(server, ignored -> false);
        }
        int agentCount = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean renderAgent = renderAgents.test(player.getUuid());
            if (renderAgent) {
                agentCount++;
            }
            players.add(new ScreenDemandEvaluator.PlayerView(
                    player.getServerWorld().getRegistryKey(), player.getX(), player.getY(),
                    player.getZ(), renderAgent));
        }
        return new Presence(List.copyOf(players), players.size() - agentCount, agentCount);
    }

    private static DemandManager.ActivationContext context(MinecraftServer server,
                                                           Presence presence) {
        DemandManager.ServerMode mode = server.isDedicated()
                ? DemandManager.ServerMode.DEDICATED
                : server.isRemote() ? DemandManager.ServerMode.LAN
                : DemandManager.ServerMode.INTEGRATED;
        return new DemandManager.ActivationContext(mode, server.isRunning(), server.isPaused(),
                presence.genuinePlayers(), presence.authorizedRenderAgents());
    }

    private record Presence(List<ScreenDemandEvaluator.PlayerView> players,
                            int genuinePlayers, int authorizedRenderAgents) {
    }
}
