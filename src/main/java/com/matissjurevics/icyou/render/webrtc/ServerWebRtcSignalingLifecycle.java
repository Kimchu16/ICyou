package com.matissjurevics.icyou.render.webrtc;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.matissjurevics.icyou.render.auth.ServerRenderAuthLifecycle;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.AssignmentState;
import com.matissjurevics.icyou.render.schedule.ServerRenderSchedulerLifecycle;
import com.matissjurevics.icyou.render.webrtc.WebRtcPeerRegistry.Binding;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Brokers authenticated non-trickle SDP without relaying media. */
public final class ServerWebRtcSignalingLifecycle {

    private static final Map<MinecraftServer, WebRtcPeerRegistry> ACTIVE =
            new IdentityHashMap<>();

    private ServerWebRtcSignalingLifecycle() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerWebRtcSignalingLifecycle.class) {
                ACTIVE.put(server, new WebRtcPeerRegistry());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerWebRtcSignalingLifecycle::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WebRtcPeerRegistry registry;
            synchronized (ServerWebRtcSignalingLifecycle.class) {
                registry = ACTIVE.remove(server);
            }
            if (registry != null) registry.clear().forEach(binding -> close(server, binding));
        });
        ServerPlayNetworking.registerGlobalReceiver(WebRtcAnswerC2SPayload.ID,
                (payload, context) -> answer(payload.answer(), context.player()));
    }

    public static synchronized Optional<WebRtcPeerRegistry> registry(MinecraftServer server) {
        return Optional.ofNullable(ACTIVE.get(server));
    }

    public static Optional<UUID> open(MinecraftServer server, UUID viewerSessionId,
                                      UUID cameraId, String offerSdp) {
        if (server.isOnThread()) {
            return openOnServerThread(server, viewerSessionId, cameraId, offerSdp);
        }
        var pending = server.submit(() -> openOnServerThread(
                server, viewerSessionId, cameraId, offerSdp));
        try {
            return pending.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException error) {
            pending.cancel(false);
            return Optional.empty();
        } catch (ExecutionException error) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> openOnServerThread(MinecraftServer server,
            UUID viewerSessionId, UUID cameraId, String offerSdp) {
        var scheduler = ServerRenderSchedulerLifecycle.scheduler(server).orElse(null);
        var auth = ServerRenderAuthLifecycle.authenticator(server).orElse(null);
        var registry = registry(server).orElse(null);
        if (scheduler == null || auth == null || registry == null) return Optional.empty();
        var assignment = scheduler.assignments().get(cameraId);
        if (assignment == null || assignment.state() == AssignmentState.ASSIGNED) {
            return Optional.empty();
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(assignment.agentId());
        var session = player == null ? null : auth.session(player.getUuid()).orElse(null);
        if (session == null || !session.sessionId().equals(assignment.sessionId())
                || !session.transports().contains(MediaTransport.WEBRTC)) return Optional.empty();
        var opened = registry.open(viewerSessionId, cameraId, assignment.jobId(),
                assignment.revision(), assignment.agentId(), assignment.sessionId(),
                offerSdp, Instant.now());
        opened.ifPresent(value -> ServerPlayNetworking.send(player,
                new WebRtcOfferS2CPayload(new WebRtcSignalingProtocol.Offer(
                        value.binding().peerId(), value.binding().jobId(),
                        value.binding().jobRevision(), cameraId, value.offerSdp()))));
        return opened.map(value -> value.binding().peerId());
    }

    private static void answer(WebRtcSignalingProtocol.Answer answer,
                               ServerPlayerEntity player) {
        var auth = ServerRenderAuthLifecycle.authenticator(player.getServer()).orElse(null);
        var registry = registry(player.getServer()).orElse(null);
        var session = auth == null ? null : auth.session(player.getUuid()).orElse(null);
        if (registry != null && session != null
                && session.transports().contains(MediaTransport.WEBRTC)) {
            registry.answer(answer.peerId(), answer.jobId(), answer.jobRevision(),
                    player.getUuid(), session.sessionId(), answer.sdp());
        }
    }

    private static void tick(MinecraftServer server) {
        var registry = registry(server).orElse(null);
        var scheduler = ServerRenderSchedulerLifecycle.scheduler(server).orElse(null);
        var auth = ServerRenderAuthLifecycle.authenticator(server).orElse(null);
        if (registry == null) return;
        registry.expire(Instant.now()).forEach(binding -> close(server, binding));
        if (scheduler == null || auth == null) {
            registry.clear().forEach(binding -> close(server, binding));
            return;
        }
        registry.removeInvalid(binding -> {
            var assignment = scheduler.assignments().get(binding.cameraId());
            var player = server.getPlayerManager().getPlayer(binding.agentId());
            var session = player == null ? null
                    : auth.session(binding.agentId()).orElse(null);
            return assignment != null && assignment.state() != AssignmentState.ASSIGNED
                    && assignment.jobId().equals(binding.jobId())
                    && assignment.revision() == binding.jobRevision()
                    && assignment.agentId().equals(binding.agentId())
                    && assignment.sessionId().equals(binding.agentSessionId())
                    && session != null
                    && session.sessionId().equals(binding.agentSessionId())
                    && session.transports().contains(MediaTransport.WEBRTC);
        }).forEach(binding -> close(server, binding));
    }

    public static void close(MinecraftServer server, Binding binding) {
        if (!server.isOnThread()) {
            server.execute(() -> close(server, binding));
            return;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(binding.agentId());
        if (player != null) ServerPlayNetworking.send(player,
                new WebRtcCloseS2CPayload(new WebRtcSignalingProtocol.Close(
                        binding.peerId(), binding.jobId(), binding.jobRevision())));
    }
}
