package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class RemoteSceneWorldRegistryTest {

    @Test
    void reusesOnlyTheExactJobRevisionAndSnapshot() {
        RemoteSceneWorldRegistry<Resource> registry = new RemoteSceneWorldRegistry<>();
        UUID jobId = UUID.randomUUID();
        AtomicInteger creations = new AtomicInteger();
        var firstSnapshot = snapshot(jobId, 2, 4);

        Resource first = registry.install(firstSnapshot,
                ignored -> new Resource(creations.incrementAndGet()));
        Resource reused = registry.install(firstSnapshot,
                ignored -> new Resource(creations.incrementAndGet()));
        Resource replacement = registry.install(snapshot(jobId, 2, 5),
                ignored -> new Resource(creations.incrementAndGet()));

        assertSame(first, reused);
        assertEquals(2, creations.get());
        assertTrue(first.closed);
        assertSame(replacement, registry.get(jobId));
    }

    @Test
    void retentionAndClearCloseEveryRemovedResource() {
        RemoteSceneWorldRegistry<Resource> registry = new RemoteSceneWorldRegistry<>();
        UUID retainedId = UUID.randomUUID();
        UUID removedId = UUID.randomUUID();
        Resource retained = registry.install(snapshot(retainedId, 0, 0),
                ignored -> new Resource(1));
        Resource removed = registry.install(snapshot(removedId, 0, 0),
                ignored -> new Resource(2));

        registry.retain(Set.of(retainedId));
        assertTrue(removed.closed);
        assertEquals(Set.of(retainedId), registry.resources().keySet());

        registry.clear();
        assertTrue(retained.closed);
        assertTrue(registry.resources().isEmpty());
    }

    private static SceneSnapshotAssembler.CompleteSnapshot snapshot(
            UUID jobId, long revision, long sequence) {
        byte[] bytes = new byte[] {1};
        var transfer = SceneSnapshotProtocol.fragment(jobId, revision, sequence,
                new CameraRef(UUID.randomUUID(), World.OVERWORLD,
                        new BlockPos(0, 70, 0)),
                0, 0, 0, 0, bytes);
        return new SceneSnapshotAssembler.CompleteSnapshot(transfer.begin(), bytes);
    }

    private static final class Resource implements AutoCloseable {
        private final int id;
        private boolean closed;

        private Resource(int id) {
            this.id = id;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
