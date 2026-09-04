package com.matissjurevics.icyou.client.agent;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotBegin;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotPart;

/** Reassembles bounded snapshot parts and exposes only digest-verified results. */
public final class SceneSnapshotAssembler {

    public record CompleteSnapshot(SnapshotBegin begin, byte[] encodedPackets) {
        public CompleteSnapshot {
            Objects.requireNonNull(begin, "begin");
            encodedPackets = Objects.requireNonNull(encodedPackets, "encodedPackets").clone();
        }

        @Override
        public byte[] encodedPackets() {
            return encodedPackets.clone();
        }
    }

    public record PartResult(boolean accepted, Optional<CompleteSnapshot> complete) {
        public PartResult {
            complete = Objects.requireNonNull(complete, "complete");
            if (!accepted && complete.isPresent()) {
                throw new IllegalArgumentException("Rejected part cannot complete a snapshot");
            }
        }
    }

    private static final class Pending {
        private final SnapshotBegin begin;
        private final Map<Integer, byte[]> parts = new LinkedHashMap<>();

        private Pending(SnapshotBegin begin) {
            this.begin = begin;
        }
    }

    private final int maxConcurrent;
    private final LinkedHashMap<UUID, Pending> pending = new LinkedHashMap<>();

    public SceneSnapshotAssembler(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("Snapshot concurrency must be positive");
        }
        this.maxConcurrent = maxConcurrent;
    }

    public synchronized void begin(SnapshotBegin begin) {
        Objects.requireNonNull(begin, "begin");
        pending.remove(begin.snapshotId());
        while (pending.size() >= maxConcurrent) {
            pending.remove(pending.keySet().iterator().next());
        }
        pending.put(begin.snapshotId(), new Pending(begin));
    }

    public synchronized PartResult part(SnapshotPart part) {
        Objects.requireNonNull(part, "part");
        Pending state = pending.get(part.snapshotId());
        if (state == null || part.index() >= state.begin.partCount()
                || part.data().length != expectedSize(state.begin, part.index())) {
            return new PartResult(false, Optional.empty());
        }
        byte[] existing = state.parts.get(part.index());
        if (existing != null && !Arrays.equals(existing, part.data())) {
            pending.remove(part.snapshotId());
            return new PartResult(false, Optional.empty());
        }
        state.parts.putIfAbsent(part.index(), part.data());
        if (state.parts.size() != state.begin.partCount()) {
            return new PartResult(true, Optional.empty());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(state.begin.totalBytes());
        for (int index = 0; index < state.begin.partCount(); index++) {
            output.writeBytes(state.parts.get(index));
        }
        byte[] complete = output.toByteArray();
        pending.remove(part.snapshotId());
        if (complete.length != state.begin.totalBytes()
                || !Arrays.equals(SceneSnapshotProtocol.sha256(complete),
                        state.begin.sha256())) {
            return new PartResult(false, Optional.empty());
        }
        return new PartResult(true,
                Optional.of(new CompleteSnapshot(state.begin, complete)));
    }

    public synchronized void clear() {
        pending.clear();
    }

    public synchronized void retainJobs(Set<UUID> jobIds) {
        Objects.requireNonNull(jobIds, "jobIds");
        pending.values().removeIf(state -> !jobIds.contains(state.begin.jobId()));
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private static int expectedSize(SnapshotBegin begin, int index) {
        int remaining = begin.totalBytes() - index * SceneSnapshotProtocol.MAX_PART_BYTES;
        return Math.min(SceneSnapshotProtocol.MAX_PART_BYTES, remaining);
    }
}
