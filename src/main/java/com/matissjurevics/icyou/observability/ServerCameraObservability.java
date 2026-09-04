package com.matissjurevics.icyou.observability;

import java.time.Instant;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.admin.ServerAdminLimitsLifecycle;
import com.matissjurevics.icyou.demand.DemandManager.Demand;
import com.matissjurevics.icyou.demand.ServerDemandLifecycle;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.lease.ServerChunkLeaseLifecycle;
import com.matissjurevics.icyou.observability.CameraEventCounters.Event;
import com.matissjurevics.icyou.overhaul.FeedLifecycleState;
import com.matissjurevics.icyou.render.auth.ServerRenderAuthLifecycle;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.AssignmentState;
import com.matissjurevics.icyou.render.schedule.ServerRenderSchedulerLifecycle;
import com.matissjurevics.icyou.render.video.ServerVideoFrameLifecycle;
import com.matissjurevics.icyou.render.webrtc.ServerWebRtcSignalingLifecycle;
import com.matissjurevics.icyou.web.ServerWebLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Owns per-server counters and logs only meaningful health-state changes. */
public final class ServerCameraObservability {

    private static final int SAMPLE_TICKS = 100;
    private static final Map<MinecraftServer, RuntimeState> ACTIVE = new IdentityHashMap<>();

    private ServerCameraObservability() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerCameraObservability.class) {
                ACTIVE.put(server, new RuntimeState());
            }
            logTransition(server, snapshot(server));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RuntimeState runtime;
            synchronized (ServerCameraObservability.class) {
                runtime = ACTIVE.get(server);
                if (runtime == null || ++runtime.ticks % SAMPLE_TICKS != 0) return;
            }
            logTransition(server, snapshot(server));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerCameraObservability.class) {
                ACTIVE.remove(server);
            }
        });
    }

    public static synchronized void record(MinecraftServer server, Event event) {
        RuntimeState runtime = ACTIVE.get(server);
        if (runtime != null) runtime.counters.record(event);
    }

    public static CameraSystemStatus snapshot(MinecraftServer server) {
        var limits = ServerAdminLimitsLifecycle.limits(server);
        GlobalDeviceRegistry devices = GlobalDeviceRegistry.get(server);
        var demands = ServerDemandLifecycle.demand(server)
                .map(manager -> manager.demands().values()).orElse(List.of());
        int activating = count(demands, FeedLifecycleState.ACTIVATING);
        int available = count(demands, FeedLifecycleState.AVAILABLE);
        int unavailable = count(demands, FeedLifecycleState.UNAVAILABLE);
        int retaining = count(demands, FeedLifecycleState.RETAINING);
        int viewers = ServerWebLifecycle.demand(server)
                .map(value -> value.sessions(Instant.now()).size()).orElse(0);
        var assignments = ServerRenderSchedulerLifecycle.scheduler(server)
                .map(value -> value.assignments().values()).orElse(List.of());
        int readyJobs = (int) assignments.stream()
                .filter(job -> job.state() == AssignmentState.AVAILABLE).count();
        CameraEventCounters.Snapshot events;
        synchronized (ServerCameraObservability.class) {
            RuntimeState runtime = ACTIVE.get(server);
            events = runtime == null ? new CameraEventCounters.Snapshot(0, 0, 0, 0)
                    : runtime.counters.snapshot();
        }
        return new CameraSystemStatus(
                devices.cameraCount(), limits.registeredCameras(),
                activating, available, unavailable, retaining,
                viewers, limits.totalViewers(),
                ServerRenderAuthLifecycle.authenticator(server)
                        .map(value -> value.sessionCount()).orElse(0),
                assignments.size(), readyJobs,
                ServerChunkLeaseLifecycle.leases(server)
                        .map(value -> value.leasedLocations().size()).orElse(0),
                ServerVideoFrameLifecycle.store(server).map(value -> value.size()).orElse(0),
                ServerWebRtcSignalingLifecycle.registry(server)
                        .map(value -> value.size()).orElse(0),
                ServerWebLifecycle.isActive(server), events);
    }

    private static int count(Collection<Demand> demands, FeedLifecycleState state) {
        return (int) demands.stream().filter(value -> value.lifecycle() == state).count();
    }

    private static void logTransition(MinecraftServer server, CameraSystemStatus status) {
        boolean changed;
        synchronized (ServerCameraObservability.class) {
            RuntimeState runtime = ACTIVE.get(server);
            changed = runtime != null && runtime.lastState != status.state();
            if (runtime != null) runtime.lastState = status.state();
        }
        if (!changed) return;
        String message = "ICyou camera system is {}: {} cameras, {} viewers, {} jobs, "
                + "{} unavailable feeds";
        if (status.state() == CameraSystemStatus.State.DEGRADED) {
            ICyouMod.LOGGER.warn(message, status.state().name().toLowerCase(),
                    status.cameras(), status.viewers(), status.jobs(),
                    status.unavailableFeeds());
        } else {
            ICyouMod.LOGGER.info(message, status.state().name().toLowerCase(),
                    status.cameras(), status.viewers(), status.jobs(),
                    status.unavailableFeeds());
        }
    }

    private static final class RuntimeState {
        private final CameraEventCounters counters = new CameraEventCounters();
        private CameraSystemStatus.State lastState;
        private long ticks;
    }
}
