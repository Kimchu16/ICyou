package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotBegin;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotPart;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class SceneSnapshotAssemblerTest {

    @Test
    void completesOutOfOrderOnlyAfterEveryPartAndDigestMatch() {
        byte[] source = new byte[SceneSnapshotProtocol.MAX_PART_BYTES + 3];
        Arrays.fill(source, (byte) 8);
        var transfer = transfer(UUID.randomUUID(), source);
        SceneSnapshotAssembler assembler = new SceneSnapshotAssembler(4);
        assembler.begin(transfer.begin());

        var lastFirst = assembler.part(transfer.parts().get(1));
        var complete = assembler.part(transfer.parts().get(0));

        assertTrue(lastFirst.accepted());
        assertTrue(lastFirst.complete().isEmpty());
        assertTrue(complete.accepted());
        assertArrayEquals(source, complete.complete().orElseThrow().encodedPackets());
        assertEquals(0, assembler.pendingCount());
    }

    @Test
    void conflictingDuplicateOrWrongDigestDiscardsTransfer() {
        byte[] large = new byte[SceneSnapshotProtocol.MAX_PART_BYTES + 1];
        Arrays.fill(large, (byte) 6);
        var conflictTransfer = transfer(UUID.randomUUID(), large);
        SceneSnapshotAssembler assembler = new SceneSnapshotAssembler(1);
        assembler.begin(conflictTransfer.begin());
        assertTrue(assembler.part(conflictTransfer.parts().get(0)).accepted());
        byte[] changed = conflictTransfer.parts().get(0).data();
        changed[0] = 7;
        assertFalse(assembler.part(new SnapshotPart(conflictTransfer.begin().snapshotId(),
                0, changed)).accepted());
        assertEquals(0, assembler.pendingCount());

        var transfer = transfer(UUID.randomUUID(), new byte[] {1, 2, 3});
        SnapshotBegin corrupt = new SnapshotBegin(UUID.randomUUID(),
                transfer.begin().jobId(), 0, 0, transfer.begin().camera(), 0, 0,
                0, 0, 3, 1, new byte[32]);
        assembler.begin(corrupt);
        var rejected = assembler.part(new SnapshotPart(corrupt.snapshotId(), 0,
                new byte[] {1, 2, 3}));
        assertFalse(rejected.accepted());
        assertEquals(0, assembler.pendingCount());
    }

    @Test
    void wrongPartSizeAndUnknownSnapshotAreRejected() {
        var transfer = transfer(UUID.randomUUID(), new byte[] {1, 2, 3});
        SceneSnapshotAssembler assembler = new SceneSnapshotAssembler(1);
        assembler.begin(transfer.begin());

        assertFalse(assembler.part(new SnapshotPart(transfer.begin().snapshotId(), 0,
                new byte[] {1, 2})).accepted());
        assertFalse(assembler.part(new SnapshotPart(UUID.randomUUID(), 0,
                new byte[] {1})).accepted());
    }

    @Test
    void concurrentLimitAndJobRetentionBoundPartialState() {
        UUID firstJob = UUID.randomUUID();
        UUID secondJob = UUID.randomUUID();
        SceneSnapshotAssembler assembler = new SceneSnapshotAssembler(1);
        assembler.begin(transfer(firstJob, new byte[] {1}).begin());
        assembler.begin(transfer(secondJob, new byte[] {2}).begin());
        assertEquals(1, assembler.pendingCount());

        assembler.retainJobs(Set.of(firstJob));

        assertEquals(0, assembler.pendingCount());
    }

    @Test
    void executorInstallsOnlyTheExactActiveJobSnapshotAndClearsItOnCancel() {
        ClientRenderJobExecutor executor = new ClientRenderJobExecutor();
        CameraRef camera = new CameraRef(UUID.randomUUID(), World.OVERWORLD,
                new BlockPos(1, 70, 2));
        JobAssignment job = new JobAssignment(UUID.randomUUID(), 2, camera,
                CameraOverhaulContracts.VIDEO_WIDTH, CameraOverhaulContracts.VIDEO_HEIGHT,
                CameraOverhaulContracts.VIDEO_FPS);
        executor.start(job);
        byte[] bytes = new byte[] {1, 2, 3};
        var transfer = SceneSnapshotProtocol.fragment(job.jobId(), job.revision(), 0,
                camera, 10, 20, 0, 0, bytes);
        var complete = new SceneSnapshotAssembler.CompleteSnapshot(
                transfer.begin(), bytes);

        assertTrue(executor.installSnapshot(complete));
        assertTrue(executor.snapshots().containsKey(job.jobId()));
        executor.cancel(job.jobId(), CancelReason.DEMAND_ENDED);
        assertTrue(executor.snapshots().isEmpty());

        assertFalse(executor.installSnapshot(complete));
    }

    private static SceneSnapshotProtocol.Transfer transfer(UUID jobId, byte[] bytes) {
        return SceneSnapshotProtocol.fragment(jobId, 0, 0,
                new CameraRef(UUID.randomUUID(), World.OVERWORLD, new BlockPos(0, 70, 0)),
                0, 0, 0, 0, bytes);
    }
}
