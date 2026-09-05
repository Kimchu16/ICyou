package com.matissjurevics.icyou.render.schedule;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.demand.ServerDemandLifecycle;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.render.auth.ServerRenderAuthLifecycle;
import com.matissjurevics.icyou.render.protocol.RenderControlS2CPayload;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobCancel;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobStatus;
import com.matissjurevics.icyou.admin.ServerAdminLimitsLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Connects the pure render scheduler to logical-server demand and networking. */
public final class ServerRenderSchedulerLifecycle {

    private static final Map<MinecraftServer, RenderScheduler> ACTIVE =
            new IdentityHashMap<>();

    private ServerRenderSchedulerLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerRenderSchedulerLifecycle::start);
        ServerTickEvents.END_SERVER_TICK.register(ServerRenderSchedulerLifecycle::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                scheduler(server).ifPresent(value ->
                        value.agentDisconnected(handler.getPlayer().getUuid())));
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerRenderSchedulerLifecycle::stop);
    }

    public static synchronized Optional<RenderScheduler> scheduler(MinecraftServer server) {
        return Optional.ofNullable(ACTIVE.get(server));
    }

    public static void handleStatus(MinecraftServer server, UUID agentId, JobStatus status) {
        ServerRenderAuthLifecycle.authenticator(server).flatMap(auth -> auth.session(agentId))
                .ifPresent(session -> scheduler(server).ifPresent(value ->
                        value.handleStatus(agentId, session.sessionId(), status)));
    }

    private static synchronized void start(MinecraftServer server) {
        ServerDemandLifecycle.demand(server).ifPresent(demand ->
                ACTIVE.put(server, new RenderScheduler(demand, new NetworkSink(server),
                        ServerAdminLimitsLifecycle.limits(server).activeCameras())));
    }

    private static synchronized void stop(MinecraftServer server) {
        RenderScheduler scheduler = ACTIVE.remove(server);
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    private static void tick(MinecraftServer server) {
        RenderScheduler scheduler;
        synchronized (ServerRenderSchedulerLifecycle.class) {
            scheduler = ACTIVE.get(server);
        }
        if (scheduler == null) {
            return;
        }
        scheduler.reconcile(cameras(server), agents(server));
    }

    private static Map<UUID, CameraRef> cameras(MinecraftServer server) {
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(server);
        Map<UUID, CameraRef> result = new LinkedHashMap<>();
        registry.terminalIds().stream().flatMap(id -> registry.camerasFor(id).stream())
                .forEach(camera -> result.put(camera.ref().deviceId(), camera.ref()));
        return result;
    }

    private static ArrayList<RenderScheduler.Agent> agents(MinecraftServer server) {
        ArrayList<RenderScheduler.Agent> result = new ArrayList<>();
        ServerRenderAuthLifecycle.authenticator(server).ifPresent(auth -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                auth.session(player.getUuid()).ifPresent(session -> result.add(
                        new RenderScheduler.Agent(player.getUuid(), session.sessionId(),
                                player.getServerWorld().getRegistryKey(), session.capacity())));
            }
        });
        return result;
    }

    private record NetworkSink(MinecraftServer server) implements RenderScheduler.MessageSink {
        @Override
        public void assign(UUID agentId, JobAssignment assignment) {
            send(agentId, new RenderControlS2CPayload(assignment));
        }

        @Override
        public void cancel(UUID agentId, JobCancel cancel) {
            send(agentId, new RenderControlS2CPayload(cancel));
        }

        private void send(UUID agentId, RenderControlS2CPayload payload) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(agentId);
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
