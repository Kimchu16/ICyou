package com.matissjurevics.icyou.client.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.client.agent.SceneSnapshotAssembler.CompleteSnapshot;
import com.matissjurevics.icyou.render.scene.SceneDeltaProtocol.Delta;

/** Holds accepted client jobs for the scene and renderer stages added next. */
public final class ClientRenderJobExecutor implements RenderAgentClient.JobExecutor {

    public static final int MAX_QUEUED_DELTAS = 256;

    public enum DeltaResult {
        ACCEPTED,
        STALE,
        GAP,
        WRONG_JOB
    }

    private final Map<UUID, JobAssignment> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, CompleteSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<Delta>> deltas = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextDeltaSequences = new ConcurrentHashMap<>();

    @Override
    public boolean start(JobAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        snapshots.remove(assignment.jobId());
        deltas.remove(assignment.jobId());
        nextDeltaSequences.remove(assignment.jobId());
        jobs.put(assignment.jobId(), assignment);
        return true;
    }

    @Override
    public void cancel(UUID jobId, CancelReason reason) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(reason, "reason");
        jobs.remove(jobId);
        snapshots.remove(jobId);
        deltas.remove(jobId);
        nextDeltaSequences.remove(jobId);
    }

    public Map<UUID, JobAssignment> jobs() {
        return Map.copyOf(jobs);
    }

    public boolean installSnapshot(CompleteSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        JobAssignment job = jobs.get(snapshot.begin().jobId());
        if (job == null || job.revision() != snapshot.begin().jobRevision()
                || !job.camera().equals(snapshot.begin().camera())) {
            return false;
        }
        snapshots.put(job.jobId(), snapshot);
        deltas.put(job.jobId(), new ArrayDeque<>());
        nextDeltaSequences.put(job.jobId(), 1L);
        return true;
    }

    public Map<UUID, CompleteSnapshot> snapshots() {
        return Map.copyOf(snapshots);
    }

    public synchronized DeltaResult installDelta(Delta delta) {
        Objects.requireNonNull(delta, "delta");
        JobAssignment job = jobs.get(delta.jobId());
        CompleteSnapshot snapshot = snapshots.get(delta.jobId());
        if (job == null || snapshot == null || job.revision() != delta.jobRevision()) {
            return DeltaResult.WRONG_JOB;
        }
        if (snapshot.begin().sequence() != delta.snapshotSequence()) {
            return DeltaResult.GAP;
        }
        ArrayDeque<Delta> queue = deltas.get(delta.jobId());
        if (queue == null) {
            return DeltaResult.WRONG_JOB;
        }
        long expected = nextDeltaSequences.getOrDefault(delta.jobId(), 1L);
        if (delta.deltaSequence() < expected) {
            return DeltaResult.STALE;
        }
        if (delta.deltaSequence() > expected || queue.size() >= MAX_QUEUED_DELTAS) {
            return DeltaResult.GAP;
        }
        queue.addLast(delta);
        nextDeltaSequences.put(delta.jobId(), expected + 1);
        return DeltaResult.ACCEPTED;
    }

    public synchronized List<Delta> drainDeltas(UUID jobId) {
        ArrayDeque<Delta> queue = deltas.get(Objects.requireNonNull(jobId, "jobId"));
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        List<Delta> result = new ArrayList<>(queue);
        queue.clear();
        return List.copyOf(result);
    }
}
