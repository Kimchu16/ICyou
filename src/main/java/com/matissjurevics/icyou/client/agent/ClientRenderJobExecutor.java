package com.matissjurevics.icyou.client.agent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;

/** Holds accepted client jobs for the scene and renderer stages added next. */
public final class ClientRenderJobExecutor implements RenderAgentClient.JobExecutor {

    private final Map<UUID, JobAssignment> jobs = new ConcurrentHashMap<>();

    @Override
    public boolean start(JobAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        jobs.put(assignment.jobId(), assignment);
        return true;
    }

    @Override
    public void cancel(UUID jobId, CancelReason reason) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(reason, "reason");
        jobs.remove(jobId);
    }

    public Map<UUID, JobAssignment> jobs() {
        return Map.copyOf(jobs);
    }
}
