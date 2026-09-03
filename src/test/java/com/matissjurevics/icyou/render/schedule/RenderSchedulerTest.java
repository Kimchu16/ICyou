package com.matissjurevics.icyou.render.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.demand.DemandManager;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.FeedLifecycleState;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobCancel;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobState;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobStatus;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class RenderSchedulerTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final DemandManager.ActivationContext ACTIVE =
            new DemandManager.ActivationContext(DemandManager.ServerMode.DEDICATED,
                    true, false, 0, 1);

    @Test
    void assignsOnlySameDimensionWithinGlobalAndAgentLimits() {
        DemandManager demand = new DemandManager();
        RecordingSink sink = new RecordingSink();
        RenderScheduler scheduler = new RenderScheduler(demand, sink);
        Map<UUID, CameraRef> cameras = new LinkedHashMap<>();
        Map<UUID, Integer> viewers = new LinkedHashMap<>();
        for (int index = 0; index < 5; index++) {
            CameraRef camera = camera(World.OVERWORLD, index);
            cameras.put(camera.deviceId(), camera);
            viewers.put(camera.deviceId(), 1);
        }
        demand.reconcile(viewers, Map.of(), ACTIVE, START);
        RenderScheduler.Agent overworld = agent(World.OVERWORLD, 2);
        RenderScheduler.Agent secondOverworld = agent(World.OVERWORLD, 2);
        RenderScheduler.Agent nether = agent(World.NETHER, 4);

        scheduler.reconcile(cameras, List.of(overworld, secondOverworld, nether));

        assertEquals(4, scheduler.assignments().size());
        assertEquals(4, sink.assignments.size());
        assertTrue(scheduler.assignments().values().stream()
                .noneMatch(job -> job.agentId().equals(nether.agentId())));
        assertEquals(2, scheduler.assignments().values().stream()
                .filter(job -> job.agentId().equals(overworld.agentId())).count());
        assertEquals(2, scheduler.assignments().values().stream()
                .filter(job -> job.agentId().equals(secondOverworld.agentId())).count());
        assertEquals(1, demand.demands().values().stream()
                .filter(feed -> feed.lifecycle() == FeedLifecycleState.UNAVAILABLE).count());
    }

    @Test
    void exactAvailableStatusPublishesFeedAndStaleStatusIsIgnored() {
        Fixture fixture = fixture();
        RenderScheduler.Assignment job = fixture.onlyAssignment();

        assertFalse(fixture.scheduler.handleStatus(fixture.agent.agentId(),
                UUID.randomUUID(), status(job, JobState.AVAILABLE)));
        assertFalse(fixture.scheduler.handleStatus(fixture.agent.agentId(),
                fixture.agent.sessionId(), new JobStatus(job.jobId(), job.revision() + 1,
                        JobState.AVAILABLE, "stale")));
        assertTrue(fixture.scheduler.handleStatus(fixture.agent.agentId(),
                fixture.agent.sessionId(), status(job, JobState.AVAILABLE)));

        assertEquals(FeedLifecycleState.AVAILABLE,
                fixture.demand.lifecycle(fixture.camera.deviceId()));
        assertEquals(RenderScheduler.AssignmentState.AVAILABLE,
                fixture.onlyAssignment().state());
        assertFalse(fixture.scheduler.handleStatus(fixture.agent.agentId(),
                fixture.agent.sessionId(), status(job, JobState.ACCEPTED)));
    }

    @Test
    void failedJobBecomesUnavailableAndMovesToAnotherAgent() {
        Fixture fixture = fixture();
        RenderScheduler.Agent replacement = agent(World.OVERWORLD, 1);
        RenderScheduler.Assignment failed = fixture.onlyAssignment();

        assertTrue(fixture.scheduler.handleStatus(fixture.agent.agentId(),
                fixture.agent.sessionId(), status(failed, JobState.FAILED)));
        assertEquals(FeedLifecycleState.UNAVAILABLE,
                fixture.demand.lifecycle(fixture.camera.deviceId()));
        assertEquals(CancelReason.REASSIGNED, fixture.sink.cancels.get(0).cancel.reason());

        fixture.demand.reconcile(Map.of(fixture.camera.deviceId(), 1), Map.of(), ACTIVE,
                START.plusSeconds(1));
        fixture.scheduler.reconcile(Map.of(fixture.camera.deviceId(), fixture.camera),
                List.of(fixture.agent, replacement));

        RenderScheduler.Assignment reassigned = fixture.onlyAssignment();
        assertEquals(replacement.agentId(), reassigned.agentId());
        assertNotEquals(failed.jobId(), reassigned.jobId());
    }

    @Test
    void disconnectMakesFeedUnavailableBeforeReassignment() {
        Fixture fixture = fixture();
        RenderScheduler.Agent replacement = agent(World.OVERWORLD, 1);

        fixture.scheduler.agentDisconnected(fixture.agent.agentId());

        assertTrue(fixture.scheduler.assignments().isEmpty());
        assertEquals(FeedLifecycleState.UNAVAILABLE,
                fixture.demand.lifecycle(fixture.camera.deviceId()));
        assertTrue(fixture.sink.cancels.isEmpty());

        fixture.demand.reconcile(Map.of(fixture.camera.deviceId(), 1), Map.of(), ACTIVE,
                START.plusSeconds(1));
        fixture.scheduler.reconcile(Map.of(fixture.camera.deviceId(), fixture.camera),
                List.of(replacement));
        assertEquals(replacement.agentId(), fixture.onlyAssignment().agentId());
    }

    @Test
    void retentionKeepsJobUntilGraceExpires() {
        Fixture fixture = fixture();
        RenderScheduler.Assignment job = fixture.onlyAssignment();
        fixture.scheduler.handleStatus(fixture.agent.agentId(), fixture.agent.sessionId(),
                status(job, JobState.AVAILABLE));

        fixture.demand.reconcile(Map.of(), Map.of(), ACTIVE, START.plusSeconds(1));
        fixture.scheduler.reconcile(Map.of(fixture.camera.deviceId(), fixture.camera),
                List.of(fixture.agent));
        assertEquals(FeedLifecycleState.RETAINING,
                fixture.demand.lifecycle(fixture.camera.deviceId()));
        assertEquals(1, fixture.scheduler.assignments().size());

        fixture.demand.reconcile(Map.of(), Map.of(), ACTIVE, START.plusSeconds(31));
        fixture.scheduler.reconcile(Map.of(fixture.camera.deviceId(), fixture.camera),
                List.of(fixture.agent));

        assertTrue(fixture.scheduler.assignments().isEmpty());
        assertEquals(CancelReason.DEMAND_ENDED,
                fixture.sink.cancels.get(0).cancel.reason());
    }

    @Test
    void cameraMovementCancelsOldJobBeforeNewAssignment() {
        Fixture fixture = fixture();
        RenderScheduler.Assignment old = fixture.onlyAssignment();
        CameraRef moved = new CameraRef(fixture.camera.deviceId(), World.OVERWORLD,
                new BlockPos(160, 70, 160));

        fixture.scheduler.reconcile(Map.of(moved.deviceId(), moved), List.of(fixture.agent));

        assertTrue(fixture.scheduler.assignments().isEmpty());
        assertEquals(CancelReason.CAMERA_MOVED, fixture.sink.cancels.get(0).cancel.reason());
        assertEquals(old.revision() + 1, fixture.sink.cancels.get(0).cancel.revision());
        assertEquals(FeedLifecycleState.UNAVAILABLE, fixture.demand.lifecycle(moved.deviceId()));

        fixture.demand.reconcile(Map.of(moved.deviceId(), 1), Map.of(), ACTIVE,
                START.plusSeconds(1));
        fixture.scheduler.reconcile(Map.of(moved.deviceId(), moved), List.of(fixture.agent));
        assertEquals(moved, fixture.onlyAssignment().camera());
    }

    @Test
    void newDemandCanReplaceARetainedJobAtTheGlobalLimit() {
        DemandManager demand = new DemandManager();
        RecordingSink sink = new RecordingSink();
        RenderScheduler scheduler = new RenderScheduler(demand, sink);
        RenderScheduler.Agent agent = agent(World.OVERWORLD, 4);
        Map<UUID, CameraRef> cameras = new LinkedHashMap<>();
        Map<UUID, Integer> firstDemand = new LinkedHashMap<>();
        for (int index = 0; index < 5; index++) {
            CameraRef camera = camera(World.OVERWORLD, index);
            cameras.put(camera.deviceId(), camera);
            if (index < 4) {
                firstDemand.put(camera.deviceId(), 1);
            }
        }
        demand.reconcile(firstDemand, Map.of(), ACTIVE, START);
        scheduler.reconcile(cameras, List.of(agent));
        UUID retained = scheduler.assignments().keySet().iterator().next();
        Map<UUID, Integer> replacementDemand = new LinkedHashMap<>(firstDemand);
        replacementDemand.remove(retained);
        UUID replacement = cameras.keySet().stream()
                .filter(id -> !firstDemand.containsKey(id)).findFirst().orElseThrow();
        replacementDemand.put(replacement, 1);

        demand.reconcile(replacementDemand, Map.of(), ACTIVE, START.plusSeconds(1));
        scheduler.reconcile(cameras, List.of(agent));

        assertTrue(scheduler.assignments().containsKey(replacement));
        assertFalse(scheduler.assignments().containsKey(retained));
        assertTrue(sink.cancels.stream().anyMatch(sent ->
                sent.cancel.reason() == CancelReason.DEMAND_ENDED));
    }

    @Test
    void serverStopCancelsEveryRemainingJob() {
        Fixture fixture = fixture();
        RenderScheduler.Assignment job = fixture.onlyAssignment();

        fixture.scheduler.stop();

        assertTrue(fixture.scheduler.assignments().isEmpty());
        assertEquals(CancelReason.SERVER_STOPPING,
                fixture.sink.cancels.get(0).cancel.reason());
        assertEquals(job.revision() + 1, fixture.sink.cancels.get(0).cancel.revision());
    }

    private static Fixture fixture() {
        DemandManager demand = new DemandManager();
        RecordingSink sink = new RecordingSink();
        RenderScheduler scheduler = new RenderScheduler(demand, sink);
        CameraRef camera = camera(World.OVERWORLD, 1);
        RenderScheduler.Agent agent = agent(World.OVERWORLD, 1);
        demand.reconcile(Map.of(camera.deviceId(), 1), Map.of(), ACTIVE, START);
        scheduler.reconcile(Map.of(camera.deviceId(), camera), List.of(agent));
        return new Fixture(demand, scheduler, sink, camera, agent);
    }

    private static CameraRef camera(net.minecraft.registry.RegistryKey<World> dimension,
                                    int offset) {
        return new CameraRef(UUID.randomUUID(), dimension,
                new BlockPos(offset * 16, 70, offset * 16));
    }

    private static RenderScheduler.Agent agent(
            net.minecraft.registry.RegistryKey<World> dimension, int capacity) {
        return new RenderScheduler.Agent(UUID.randomUUID(), UUID.randomUUID(),
                dimension, capacity);
    }

    private static JobStatus status(RenderScheduler.Assignment assignment, JobState state) {
        return new JobStatus(assignment.jobId(), assignment.revision(), state, "test");
    }

    private record Fixture(DemandManager demand, RenderScheduler scheduler,
                           RecordingSink sink, CameraRef camera,
                           RenderScheduler.Agent agent) {
        private RenderScheduler.Assignment onlyAssignment() {
            assertEquals(1, scheduler.assignments().size());
            return scheduler.assignments().values().iterator().next();
        }
    }

    private static final class RecordingSink implements RenderScheduler.MessageSink {
        private final List<SentAssignment> assignments = new ArrayList<>();
        private final List<SentCancel> cancels = new ArrayList<>();

        @Override
        public void assign(UUID agentId, JobAssignment assignment) {
            assignments.add(new SentAssignment(agentId, assignment));
        }

        @Override
        public void cancel(UUID agentId, JobCancel cancel) {
            cancels.add(new SentCancel(agentId, cancel));
        }
    }

    private record SentAssignment(UUID agentId, JobAssignment assignment) {
    }

    private record SentCancel(UUID agentId, JobCancel cancel) {
    }
}
