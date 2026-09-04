package com.matissjurevics.icyou.client.agent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.client.agent.SceneSnapshotAssembler.CompleteSnapshot;

/** Holds accepted client jobs for the scene and renderer stages added next. */
public final class ClientRenderJobExecutor implements RenderAgentClient.JobExecutor {

    private final Map<UUID, JobAssignment> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, CompleteSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public boolean start(JobAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        snapshots.remove(assignment.jobId());
        jobs.put(assignment.jobId(), assignment);
        return true;
    }

    @Override
    public void cancel(UUID jobId, CancelReason reason) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(reason, "reason");
        jobs.remove(jobId);
        snapshots.remove(jobId);
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
        return true;
    }

    public Map<UUID, CompleteSnapshot> snapshots() {
        return Map.copyOf(snapshots);
    }
}
