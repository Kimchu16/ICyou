package com.matissjurevics.icyou.render.schedule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.demand.DemandManager;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.overhaul.FeedLifecycleState;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobCancel;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobState;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobStatus;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/** Assigns retained camera feeds to authenticated, same-dimension render agents. */
public final class RenderScheduler {

    public interface MessageSink {
        void assign(UUID agentId, JobAssignment assignment);

        void cancel(UUID agentId, JobCancel cancel);
    }

    public record Agent(UUID agentId, UUID sessionId, RegistryKey<World> dimension,
                        int capacity) {
        public Agent {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(dimension, "dimension");
            if (capacity < 1 || capacity > CameraOverhaulContracts.MAX_ACTIVE_CAMERAS) {
                throw new IllegalArgumentException("Invalid render-agent capacity: " + capacity);
            }
        }
    }

    public enum AssignmentState {
        ASSIGNED,
        ACCEPTED,
        AVAILABLE
    }

    public record Assignment(UUID jobId, long revision, CameraRef camera,
                             UUID agentId, UUID sessionId, AssignmentState state) {
        public Assignment {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(camera, "camera");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(state, "state");
        }

        private Assignment withState(AssignmentState next) {
            return new Assignment(jobId, revision, camera, agentId, sessionId, next);
        }
    }

    private record FailedPair(UUID cameraId, UUID sessionId) {
    }

    private final DemandManager demand;
    private final MessageSink messages;
    private final int activeCameraLimit;
    private final Map<UUID, Assignment> byCamera = new LinkedHashMap<>();
    private final Set<FailedPair> failedPairs = new LinkedHashSet<>();

    public RenderScheduler(DemandManager demand, MessageSink messages) {
        this(demand, messages, CameraOverhaulContracts.MAX_ACTIVE_CAMERAS);
    }

    public RenderScheduler(DemandManager demand, MessageSink messages,
                           int activeCameraLimit) {
        this.demand = Objects.requireNonNull(demand, "demand");
        this.messages = Objects.requireNonNull(messages, "messages");
        if (activeCameraLimit < 1) {
            throw new IllegalArgumentException("Active camera limit must be positive");
        }
        this.activeCameraLimit = activeCameraLimit;
    }

    public synchronized void reconcile(Map<UUID, CameraRef> cameras,
                                       Collection<Agent> agentCollection) {
        Objects.requireNonNull(cameras, "cameras");
        Objects.requireNonNull(agentCollection, "agents");
        Map<UUID, Agent> agents = agents(agentCollection);
        Map<UUID, DemandManager.Demand> demands = demand.demands();
        failedPairs.removeIf(pair -> !sameSessionExists(pair, agents)
                || !isDemanded(demands.get(pair.cameraId())));

        for (Assignment assignment : new ArrayList<>(byCamera.values())) {
            DemandManager.Demand feed = demands.get(assignment.camera().deviceId());
            CameraRef currentCamera = cameras.get(assignment.camera().deviceId());
            Agent agent = agents.get(assignment.agentId());
            CancelReason reason = invalidReason(assignment, feed, currentCamera, agent);
            if (reason != null) {
                cancel(assignment, reason, agent != null);
                unavailableIfDemanded(feed);
            }
        }

        ArrayList<DemandManager.Demand> candidates = new ArrayList<>(demands.values());
        candidates.sort(Comparator.comparing(feed -> feed.cameraId().toString()));
        for (DemandManager.Demand feed : candidates) {
            DemandManager.Demand current = demand.demand(feed.cameraId()).orElse(feed);
            if (!current.demanded()
                    || current.lifecycle() != FeedLifecycleState.ACTIVATING
                    || byCamera.containsKey(current.cameraId())) {
                continue;
            }
            CameraRef camera = cameras.get(current.cameraId());
            if (camera == null) {
                demand.markUnavailable(current.cameraId());
                continue;
            }
            Optional<Agent> selected = selectAgent(camera, agents.values());
            if (selected.isEmpty() && evictRetaining(camera, agents, demands)) {
                selected = selectAgent(camera, agents.values());
            }
            if (selected.isEmpty()) {
                demand.markUnavailable(current.cameraId());
                continue;
            }
            assign(camera, selected.orElseThrow());
        }
    }

    public synchronized boolean handleStatus(UUID agentId, UUID sessionId,
                                             JobStatus status) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(status, "status");
        Assignment assignment = byCamera.values().stream()
                .filter(candidate -> candidate.jobId().equals(status.jobId()))
                .findFirst().orElse(null);
        if (assignment == null || !assignment.agentId().equals(agentId)
                || !assignment.sessionId().equals(sessionId)
                || assignment.revision() != status.revision()) {
            return false;
        }
        DemandManager.Demand feed = demand.demand(assignment.camera().deviceId()).orElse(null);
        if (!isDemanded(feed)) {
            return false;
        }
        switch (status.state()) {
            case ACCEPTED -> {
                if (assignment.state() == AssignmentState.AVAILABLE) {
                    return false;
                }
                byCamera.put(assignment.camera().deviceId(),
                        assignment.withState(AssignmentState.ACCEPTED));
            }
            case AVAILABLE -> {
                if (feed.lifecycle() != FeedLifecycleState.ACTIVATING) {
                    return false;
                }
                demand.markAvailable(feed.cameraId());
                byCamera.put(feed.cameraId(), assignment.withState(AssignmentState.AVAILABLE));
            }
            case FAILED -> {
                demand.markUnavailable(feed.cameraId());
                failedPairs.add(new FailedPair(feed.cameraId(), sessionId));
                cancel(assignment, CancelReason.REASSIGNED, true);
            }
        }
        return true;
    }

    public synchronized void agentDisconnected(UUID agentId) {
        Objects.requireNonNull(agentId, "agentId");
        Map<UUID, DemandManager.Demand> demands = demand.demands();
        byCamera.values().stream().filter(job -> job.agentId().equals(agentId))
                .toList().forEach(job -> {
                    byCamera.remove(job.camera().deviceId());
                    unavailableIfDemanded(demands.get(job.camera().deviceId()));
                });
    }

    public synchronized boolean failJob(UUID jobId) {
        Objects.requireNonNull(jobId, "jobId");
        Assignment assignment = byCamera.values().stream()
                .filter(job -> job.jobId().equals(jobId)).findFirst().orElse(null);
        if (assignment == null) {
            return false;
        }
        DemandManager.Demand feed = demand.demand(assignment.camera().deviceId()).orElse(null);
        unavailableIfDemanded(feed);
        cancel(assignment, CancelReason.REASSIGNED, true);
        return true;
    }

    public synchronized Map<UUID, Assignment> assignments() {
        return Map.copyOf(byCamera);
    }

    public synchronized void stop() {
        byCamera.values().stream().toList().forEach(job ->
                cancel(job, CancelReason.SERVER_STOPPING, true));
        failedPairs.clear();
    }

    private void assign(CameraRef camera, Agent agent) {
        Assignment assignment = new Assignment(UUID.randomUUID(), 0, camera,
                agent.agentId(), agent.sessionId(), AssignmentState.ASSIGNED);
        byCamera.put(camera.deviceId(), assignment);
        messages.assign(agent.agentId(), new JobAssignment(assignment.jobId(),
                assignment.revision(), camera, CameraOverhaulContracts.VIDEO_WIDTH,
                CameraOverhaulContracts.VIDEO_HEIGHT, CameraOverhaulContracts.VIDEO_FPS));
    }

    private void cancel(Assignment assignment, CancelReason reason, boolean notify) {
        byCamera.remove(assignment.camera().deviceId());
        if (notify) {
            messages.cancel(assignment.agentId(), new JobCancel(assignment.jobId(),
                    assignment.revision() + 1, reason));
        }
    }

    private Optional<Agent> selectAgent(CameraRef camera, Collection<Agent> agents) {
        if (byCamera.size() >= activeCameraLimit) {
            return Optional.empty();
        }
        return agents.stream()
                .filter(agent -> agent.dimension().equals(camera.dimension()))
                .filter(agent -> !failedPairs.contains(
                        new FailedPair(camera.deviceId(), agent.sessionId())))
                .filter(agent -> load(agent.agentId()) < agent.capacity())
                .min(Comparator.comparingInt((Agent agent) -> load(agent.agentId()))
                        .thenComparing(agent -> agent.agentId().toString()));
    }

    private boolean evictRetaining(CameraRef camera, Map<UUID, Agent> agents,
                                    Map<UUID, DemandManager.Demand> demands) {
        Set<UUID> compatibleAgents = agents.values().stream()
                .filter(agent -> agent.dimension().equals(camera.dimension()))
                .filter(agent -> !failedPairs.contains(
                        new FailedPair(camera.deviceId(), agent.sessionId())))
                .map(Agent::agentId).collect(java.util.stream.Collectors.toSet());
        if (compatibleAgents.isEmpty()) {
            return false;
        }
        boolean compatibleAgentHasRoom = compatibleAgents.stream().anyMatch(agentId ->
                load(agentId) < agents.get(agentId).capacity());
        Assignment candidate = byCamera.values().stream()
                .filter(job -> {
                    DemandManager.Demand feed = demands.get(job.camera().deviceId());
                    return feed != null && feed.lifecycle() == FeedLifecycleState.RETAINING;
                })
                .filter(job -> (compatibleAgentHasRoom
                        && byCamera.size() >= activeCameraLimit)
                        || compatibleAgents.contains(job.agentId()))
                .findFirst().orElse(null);
        if (candidate == null) {
            return false;
        }
        cancel(candidate, CancelReason.DEMAND_ENDED, agents.containsKey(candidate.agentId()));
        return true;
    }

    private int load(UUID agentId) {
        return (int) byCamera.values().stream()
                .filter(job -> job.agentId().equals(agentId)).count();
    }

    private static Map<UUID, Agent> agents(Collection<Agent> source) {
        Map<UUID, Agent> result = new LinkedHashMap<>();
        for (Agent agent : source) {
            Objects.requireNonNull(agent, "agent");
            Agent previous = result.put(agent.agentId(), agent);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate render agent: " + agent.agentId());
            }
        }
        return result;
    }

    private static CancelReason invalidReason(Assignment assignment,
                                              DemandManager.Demand feed,
                                              CameraRef camera, Agent agent) {
        if (feed == null || feed.lifecycle() == FeedLifecycleState.INACTIVE) {
            return CancelReason.DEMAND_ENDED;
        }
        if (feed.lifecycle() == FeedLifecycleState.UNAVAILABLE) {
            return CancelReason.REASSIGNED;
        }
        if (camera == null) {
            return CancelReason.DEMAND_ENDED;
        }
        if (!assignment.camera().equals(camera)) {
            return CancelReason.CAMERA_MOVED;
        }
        if (agent == null || !assignment.sessionId().equals(agent.sessionId())
                || !agent.dimension().equals(camera.dimension())) {
            return CancelReason.REASSIGNED;
        }
        return null;
    }

    private static boolean sameSessionExists(FailedPair pair, Map<UUID, Agent> agents) {
        return agents.values().stream().anyMatch(agent ->
                agent.sessionId().equals(pair.sessionId()));
    }

    private static boolean isDemanded(DemandManager.Demand feed) {
        return feed != null && feed.demanded();
    }

    private void unavailableIfDemanded(DemandManager.Demand feed) {
        if (isDemanded(feed) && feed.lifecycle() != FeedLifecycleState.UNAVAILABLE) {
            demand.markUnavailable(feed.cameraId());
        }
    }
}
