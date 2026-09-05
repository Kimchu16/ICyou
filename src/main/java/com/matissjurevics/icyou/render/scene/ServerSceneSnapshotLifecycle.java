package com.matissjurevics.icyou.render.scene;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.render.auth.RenderAgentAuthenticator;
import com.matissjurevics.icyou.render.auth.ServerRenderAuthLifecycle;
import com.matissjurevics.icyou.render.schedule.RenderScheduler;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.AssignmentState;
import com.matissjurevics.icyou.render.schedule.ServerRenderSchedulerLifecycle;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.Transfer;
import com.matissjurevics.icyou.admin.ServerAdminLimitsLifecycle;
import com.matissjurevics.icyou.observability.CameraEventCounters.Event;
import com.matissjurevics.icyou.observability.ServerCameraObservability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Captures and sends at most one bounded snapshot part per server tick. */
public final class ServerSceneSnapshotLifecycle {

    public record SnapshotProgress(long sequence, boolean delivered) {
    }

    private record JobKey(UUID jobId, long revision, UUID sessionId) {
        private static JobKey of(Assignment assignment) {
            return new JobKey(assignment.jobId(), assignment.revision(),
                    assignment.sessionId());
        }
    }

    private static final class Outbound {
        private final JobKey key;
        private final UUID agentId;
        private final Transfer transfer;
        private boolean began;
        private int nextPart;

        private Outbound(JobKey key, UUID agentId, Transfer transfer) {
            this.key = key;
            this.agentId = agentId;
            this.transfer = transfer;
        }
    }

    private static final class State {
        private final Map<JobKey, Long> sent = new LinkedHashMap<>();
        private long nextSequence;
        private Outbound outbound;
    }

    private static final Map<MinecraftServer, State> ACTIVE = new IdentityHashMap<>();

    private ServerSceneSnapshotLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerSceneSnapshotLifecycle.class) {
                ACTIVE.put(server, new State());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerSceneSnapshotLifecycle::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerSceneSnapshotLifecycle.class) {
                ACTIVE.remove(server);
            }
        });
    }

    public static synchronized java.util.Optional<SnapshotProgress> progress(
            MinecraftServer server, Assignment assignment) {
        State state = ACTIVE.get(server);
        if (state == null) {
            return java.util.Optional.empty();
        }
        JobKey key = JobKey.of(assignment);
        Long sentSequence = state.sent.get(key);
        if (sentSequence != null) {
            return java.util.Optional.of(new SnapshotProgress(sentSequence, true));
        }
        if (state.outbound != null && state.outbound.key.equals(key)) {
            return java.util.Optional.of(new SnapshotProgress(
                    state.outbound.transfer.begin().sequence(), false));
        }
        return java.util.Optional.empty();
    }

    private static void tick(MinecraftServer server) {
        State state;
        synchronized (ServerSceneSnapshotLifecycle.class) {
            state = ACTIVE.get(server);
        }
        RenderScheduler scheduler = ServerRenderSchedulerLifecycle.scheduler(server).orElse(null);
        RenderAgentAuthenticator authentication = ServerRenderAuthLifecycle.authenticator(server)
                .orElse(null);
        if (state == null || scheduler == null || authentication == null) {
            return;
        }
        Map<UUID, Assignment> assignments = scheduler.assignments();
        Set<JobKey> activeKeys = assignments.values().stream().map(JobKey::of)
                .collect(java.util.stream.Collectors.toSet());
        state.sent.keySet().retainAll(activeKeys);
        if (state.outbound != null && !activeKeys.contains(state.outbound.key)) {
            state.outbound = null;
        }
        if (state.outbound != null) {
            sendNext(server, state);
            return;
        }

        Assignment assignment = assignments.values().stream()
                .filter(job -> job.state() == AssignmentState.ACCEPTED)
                .filter(job -> !state.sent.containsKey(JobKey.of(job)))
                .min(Comparator.comparing(job -> job.jobId().toString())).orElse(null);
        if (assignment == null) {
            return;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(assignment.agentId());
        if (player == null || authentication.session(player.getUuid())
                .filter(session -> session.sessionId().equals(assignment.sessionId())).isEmpty()) {
            return;
        }
        try {
            Transfer transfer = ServerSceneSnapshotEncoder.capture(server, assignment, player,
                    authentication, state.nextSequence++,
                    ServerAdminLimitsLifecycle.limits(server).simulatedChunkDiameter());
            state.outbound = new Outbound(JobKey.of(assignment), assignment.agentId(), transfer);
            sendNext(server, state);
        } catch (ServerSceneSnapshotEncoder.SnapshotNotReadyException ignored) {
            // Chunk leases are asynchronous; retry on a later tick.
        } catch (RuntimeException error) {
            ServerCameraObservability.record(server, Event.SCENE_FAILURE);
            ICyouMod.LOGGER.error("Scene snapshot failed for camera {}",
                    assignment.camera().deviceId(), error);
            scheduler.failJob(assignment.jobId());
        }
    }

    private static void sendNext(MinecraftServer server, State state) {
        Outbound outbound = state.outbound;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(outbound.agentId);
        if (player == null) {
            state.outbound = null;
            return;
        }
        if (!outbound.began) {
            ServerPlayNetworking.send(player,
                    new SceneSnapshotS2CPayload(outbound.transfer.begin()));
            outbound.began = true;
            return;
        }
        ServerPlayNetworking.send(player, new SceneSnapshotS2CPayload(
                outbound.transfer.parts().get(outbound.nextPart++)));
        if (outbound.nextPart == outbound.transfer.parts().size()) {
            state.sent.put(outbound.key, outbound.transfer.begin().sequence());
            state.outbound = null;
        }
    }
}
