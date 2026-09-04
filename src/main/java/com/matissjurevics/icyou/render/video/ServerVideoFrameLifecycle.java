package com.matissjurevics.icyou.render.video;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.matissjurevics.icyou.render.auth.ServerRenderAuthLifecycle;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.schedule.ServerRenderSchedulerLifecycle;
import com.matissjurevics.icyou.render.video.ServerVideoFrameStore.JobKey;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Authenticates incoming frames and binds their lifetime to scheduler jobs. */
public final class ServerVideoFrameLifecycle {

    private static final Map<MinecraftServer, ServerVideoFrameStore> ACTIVE =
            new IdentityHashMap<>();

    private ServerVideoFrameLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerVideoFrameLifecycle.class) {
                ACTIVE.put(server, new ServerVideoFrameStore());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerVideoFrameLifecycle::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerVideoFrameLifecycle.class) {
                ServerVideoFrameStore store = ACTIVE.remove(server);
                if (store != null) {
                    store.clear();
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(VideoFrameC2SPayload.ID,
                (payload, context) -> handle(payload, context.player()));
    }

    public static synchronized Optional<ServerVideoFrameStore> store(
            MinecraftServer server) {
        return Optional.ofNullable(ACTIVE.get(server));
    }

    private static void handle(VideoFrameC2SPayload payload, ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        var authentication = ServerRenderAuthLifecycle.authenticator(server).orElse(null);
        var scheduler = ServerRenderSchedulerLifecycle.scheduler(server).orElse(null);
        ServerVideoFrameStore frames = store(server).orElse(null);
        if (authentication == null || scheduler == null || frames == null) {
            return;
        }
        var session = authentication.session(player.getUuid()).orElse(null);
        if (session == null) {
            return;
        }
        var incoming = payload.frame();
        Assignment assignment = scheduler.assignments().values().stream()
                .filter(job -> job.jobId().equals(incoming.jobId()))
                .findFirst().orElse(null);
        if (!VideoFrameAuthorization.permits(player.getUuid(), session, assignment,
                incoming)) {
            return;
        }
        frames.accept(incoming, System.currentTimeMillis());
    }

    private static void tick(MinecraftServer server) {
        ServerVideoFrameStore frames = store(server).orElse(null);
        var scheduler = ServerRenderSchedulerLifecycle.scheduler(server).orElse(null);
        if (frames == null || scheduler == null) {
            return;
        }
        Set<JobKey> jobs = scheduler.assignments().values().stream()
                .map(assignment -> new JobKey(assignment.jobId(), assignment.revision()))
                .collect(java.util.stream.Collectors.toSet());
        frames.retain(jobs);
    }
}
