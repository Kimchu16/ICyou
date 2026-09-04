package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.matissjurevics.icyou.client.agent.ClientRenderJobExecutor.DeltaResult;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.scene.SceneDeltaProtocol.Delta;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class ClientRenderJobExecutorDeltaTest {
    private final CameraRef camera = new CameraRef(UUID.randomUUID(), World.OVERWORLD,
            new BlockPos(4, 70, 8));
    private final JobAssignment job = new JobAssignment(UUID.randomUUID(), 2, camera,
            CameraOverhaulContracts.VIDEO_WIDTH, CameraOverhaulContracts.VIDEO_HEIGHT,
            CameraOverhaulContracts.VIDEO_FPS);
    private final ClientRenderJobExecutor executor = new ClientRenderJobExecutor();

    @BeforeEach
    void installJobAndSnapshot() {
        executor.start(job);
        byte[] bytes = new byte[] {1};
        var transfer = SceneSnapshotProtocol.fragment(job.jobId(), job.revision(), 9,
                camera, 0, 0, 0, 0, bytes);
        assertTrue(executor.installSnapshot(new SceneSnapshotAssembler.CompleteSnapshot(
                transfer.begin(), bytes)));
    }

    @Test
    void acceptsOnlyConsecutiveUpdatesAcrossDrains() {
        assertEquals(DeltaResult.ACCEPTED, executor.installDelta(delta(1)));
        assertEquals(DeltaResult.ACCEPTED, executor.installDelta(delta(2)));
        assertEquals(2, executor.drainDeltas(job.jobId()).size());
        assertEquals(DeltaResult.ACCEPTED, executor.installDelta(delta(3)));
        assertEquals(DeltaResult.STALE, executor.installDelta(delta(2)));
    }

    @Test
    void detectsSequenceAndSnapshotGaps() {
        assertEquals(DeltaResult.GAP, executor.installDelta(delta(2)));
        Delta wrongSnapshot = new Delta(job.jobId(), job.revision(), 10, 1,
                0, 0, 0, 0, new byte[0]);
        assertEquals(DeltaResult.GAP, executor.installDelta(wrongSnapshot));
    }

    @Test
    void rejectsUnknownOrCancelledJobs() {
        assertEquals(DeltaResult.WRONG_JOB, executor.installDelta(new Delta(
                UUID.randomUUID(), job.revision(), 9, 1, 0, 0, 0, 0, new byte[0])));
        executor.cancel(job.jobId(), CancelReason.DEMAND_ENDED);
        assertEquals(DeltaResult.WRONG_JOB, executor.installDelta(delta(1)));
    }

    @Test
    void boundsQueuedUpdates() {
        for (int sequence = 1; sequence <= ClientRenderJobExecutor.MAX_QUEUED_DELTAS;
                sequence++) {
            assertEquals(DeltaResult.ACCEPTED, executor.installDelta(delta(sequence)));
        }
        assertEquals(DeltaResult.GAP, executor.installDelta(
                delta(ClientRenderJobExecutor.MAX_QUEUED_DELTAS + 1L)));
    }

    private Delta delta(long sequence) {
        return new Delta(job.jobId(), job.revision(), 9, sequence,
                sequence, sequence, 0, 0, new byte[0]);
    }
}
