package com.matissjurevics.icyou.render.audio;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.render.auth.ServerRenderAuthLifecycle;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.schedule.ServerRenderSchedulerLifecycle;
import com.matissjurevics.icyou.render.scene.ServerSceneSnapshotLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;

/** Routes camera-audible world sounds to the exact assigned render scene. */
public final class ServerAudioSceneLifecycle {

    private record JobKey(UUID jobId, long revision, UUID sessionId) {
        private static JobKey of(Assignment assignment) {
            return new JobKey(assignment.jobId(), assignment.revision(),
                    assignment.sessionId());
        }
    }

    private static final class Sequence {
        private final long snapshot;
        private long nextBatch = 1;

        private Sequence(long snapshot) {
            this.snapshot = snapshot;
        }
    }

    private static final Map<MinecraftServer, Map<JobKey, Sequence>> ACTIVE =
            new IdentityHashMap<>();

    private ServerAudioSceneLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerAudioSceneLifecycle.class) {
                ACTIVE.put(server, new LinkedHashMap<>());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerAudioSceneLifecycle::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerAudioSceneLifecycle.class) {
                ACTIVE.remove(server);
            }
            AudioSceneJournal.clear(server);
        });
    }

    private static void tick(MinecraftServer server) {
        Map<JobKey, Sequence> sequences;
        synchronized (ServerAudioSceneLifecycle.class) {
            sequences = ACTIVE.get(server);
        }
        var scheduler = ServerRenderSchedulerLifecycle.scheduler(server).orElse(null);
        var authentication = ServerRenderAuthLifecycle.authenticator(server).orElse(null);
        var captures = AudioSceneJournal.drain(server);
        if (sequences == null || scheduler == null || authentication == null) {
            return;
        }
        Map<UUID, Assignment> assignments = scheduler.assignments();
        Set<JobKey> active = assignments.values().stream().map(JobKey::of)
                .collect(java.util.stream.Collectors.toSet());
        sequences.keySet().retainAll(active);

        for (Assignment assignment : assignments.values().stream()
                .sorted(Comparator.comparing(job -> job.jobId().toString())).toList()) {
            var progress = ServerSceneSnapshotLifecycle.progress(server, assignment)
                    .orElse(null);
            var world = server.getWorld(assignment.camera().dimension());
            var player = server.getPlayerManager().getPlayer(assignment.agentId());
            AudioSceneJournal.Capture capture = world == null ? null : captures.get(world);
            if (progress == null || !progress.delivered() || player == null
                    || capture == null || capture.events().isEmpty()) {
                continue;
            }
            var session = authentication.session(player.getUuid()).orElse(null);
            if (!AudioSceneAuthorization.permits(player.getUuid(), session, assignment)) {
                continue;
            }
            double cameraX = assignment.camera().position().getX() + 0.5;
            double cameraY = assignment.camera().position().getY() + 0.5;
            double cameraZ = assignment.camera().position().getZ() + 0.5;
            AudioSceneSelector.Selection selected = AudioSceneSelector.select(capture,
                    cameraX, cameraY, cameraZ, authentication::isAuthenticated);
            if (selected.events().isEmpty()) {
                continue;
            }
            JobKey key = JobKey.of(assignment);
            Sequence sequence = sequences.get(key);
            if (sequence == null || sequence.snapshot != progress.sequence()) {
                sequence = new Sequence(progress.sequence());
                sequences.put(key, sequence);
            }
            var batch = new AudioSceneProtocol.Batch(assignment.jobId(),
                    assignment.revision(), progress.sequence(), sequence.nextBatch++,
                    world.getTime(), selected.truncated(), selected.events());
            ServerPlayNetworking.send(player, new AudioSceneS2CPayload(batch));
        }
    }
}
