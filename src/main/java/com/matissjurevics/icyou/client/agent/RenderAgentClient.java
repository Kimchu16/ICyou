package com.matissjurevics.icyou.client.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.render.auth.RenderAgentProofs;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AgentHello;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthChallenge;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthOutcome;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthProof;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthResult;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.ClientMessage;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobCancel;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobState;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobStatus;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/** Client-side authentication and bounded render-job control state. */
public final class RenderAgentClient {

    private static final int MAX_REVISION_HISTORY = 64;

    public interface MessageSink {
        void send(ClientMessage message);
    }

    public interface JobExecutor {
        boolean start(JobAssignment assignment);

        void cancel(UUID jobId, CancelReason reason);
    }

    public enum State {
        DISCONNECTED,
        HELLO_SENT,
        PROOF_SENT,
        AUTHENTICATED,
        DENIED
    }

    private final RenderAgentConfig.Settings settings;
    private final MessageSink messages;
    private final JobExecutor executor;
    private final Map<UUID, JobAssignment> activeJobs = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Long> revisions = new LinkedHashMap<>();
    private State state = State.DISCONNECTED;
    private UUID minecraftId;
    private UUID challengeId;
    private UUID sessionId;

    public RenderAgentClient(RenderAgentConfig.Settings settings, MessageSink messages,
                             JobExecutor executor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public synchronized boolean connect(UUID playerId) {
        disconnect();
        if (!settings.enabled()) {
            return false;
        }
        minecraftId = Objects.requireNonNull(playerId, "playerId");
        state = State.HELLO_SENT;
        messages.send(new AgentHello(settings.credential().orElseThrow().credentialId(),
                settings.capacity(), settings.transports()));
        return true;
    }

    public synchronized boolean challenge(AuthChallenge challenge) {
        Objects.requireNonNull(challenge, "challenge");
        if (state != State.HELLO_SENT || minecraftId == null) {
            return false;
        }
        challengeId = challenge.challengeId();
        byte[] proof = RenderAgentProofs.createProof(settings.credential().orElseThrow(),
                challenge.challengeId(), challenge.nonce(), minecraftId);
        state = State.PROOF_SENT;
        messages.send(new AuthProof(challenge.challengeId(), proof));
        return true;
    }

    public synchronized boolean authentication(AuthResult result) {
        Objects.requireNonNull(result, "result");
        if (state != State.PROOF_SENT || !result.challengeId().equals(challengeId)) {
            return false;
        }
        challengeId = null;
        if (result.outcome() == AuthOutcome.ACCEPTED) {
            sessionId = result.sessionId().orElseThrow();
            state = State.AUTHENTICATED;
        } else {
            sessionId = null;
            state = State.DENIED;
        }
        return true;
    }

    public synchronized boolean assign(JobAssignment assignment,
                                       RegistryKey<World> currentDimension) {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(currentDimension, "currentDimension");
        if (state != State.AUTHENTICATED) {
            return false;
        }
        Long knownRevision = revisions.get(assignment.jobId());
        if (knownRevision != null && assignment.revision() <= knownRevision) {
            return false;
        }
        JobAssignment previous = activeJobs.get(assignment.jobId());
        if (previous != null) {
            executor.cancel(previous.jobId(), CancelReason.REASSIGNED);
            activeJobs.remove(previous.jobId());
        }
        if (!assignment.camera().dimension().equals(currentDimension)) {
            fail(assignment, "camera is in another dimension");
            return false;
        }
        if (previous == null && activeJobs.size() >= settings.capacity()) {
            fail(assignment, "render-agent capacity reached");
            return false;
        }
        remember(assignment.jobId(), assignment.revision());
        boolean started;
        try {
            started = executor.start(assignment);
        } catch (RuntimeException error) {
            try {
                executor.cancel(assignment.jobId(), CancelReason.REASSIGNED);
            } catch (RuntimeException ignored) {
                // Preserve the protocol failure report even if local cleanup also fails.
            }
            messages.send(new JobStatus(assignment.jobId(), assignment.revision(),
                    JobState.FAILED, "render executor failed to start"));
            return false;
        }
        if (!started) {
            messages.send(new JobStatus(assignment.jobId(), assignment.revision(),
                    JobState.FAILED, "render executor rejected the job"));
            return false;
        }
        activeJobs.put(assignment.jobId(), assignment);
        messages.send(new JobStatus(assignment.jobId(), assignment.revision(),
                JobState.ACCEPTED, ""));
        return true;
    }

    public synchronized boolean cancel(JobCancel cancel) {
        Objects.requireNonNull(cancel, "cancel");
        if (state != State.AUTHENTICATED) {
            return false;
        }
        Long knownRevision = revisions.get(cancel.jobId());
        if (knownRevision != null && cancel.revision() <= knownRevision) {
            return false;
        }
        JobAssignment assignment = activeJobs.remove(cancel.jobId());
        remember(cancel.jobId(), cancel.revision());
        if (assignment == null) {
            return false;
        }
        executor.cancel(cancel.jobId(), cancel.reason());
        return true;
    }

    public synchronized boolean markAvailable(UUID jobId) {
        JobAssignment assignment = activeJobs.get(Objects.requireNonNull(jobId, "jobId"));
        if (assignment == null || state != State.AUTHENTICATED) {
            return false;
        }
        messages.send(new JobStatus(jobId, assignment.revision(), JobState.AVAILABLE, ""));
        return true;
    }

    public synchronized boolean markFailed(UUID jobId, String detail) {
        String safeDetail = safeDetail(detail);
        JobAssignment assignment = activeJobs.remove(Objects.requireNonNull(jobId, "jobId"));
        if (assignment == null || state != State.AUTHENTICATED) {
            return false;
        }
        executor.cancel(jobId, CancelReason.REASSIGNED);
        messages.send(new JobStatus(jobId, assignment.revision(), JobState.FAILED, safeDetail));
        return true;
    }

    public synchronized void disconnect() {
        activeJobs.values().stream().toList().forEach(job ->
                executor.cancel(job.jobId(), CancelReason.SERVER_STOPPING));
        activeJobs.clear();
        revisions.clear();
        minecraftId = null;
        challengeId = null;
        sessionId = null;
        state = State.DISCONNECTED;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized Optional<UUID> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    public synchronized Map<UUID, JobAssignment> activeJobs() {
        return Map.copyOf(activeJobs);
    }

    private void fail(JobAssignment assignment, String detail) {
        remember(assignment.jobId(), assignment.revision());
        messages.send(new JobStatus(assignment.jobId(), assignment.revision(),
                JobState.FAILED, detail));
    }

    private void remember(UUID jobId, long revision) {
        revisions.put(jobId, revision);
        while (revisions.size() > MAX_REVISION_HISTORY) {
            UUID oldest = revisions.keySet().iterator().next();
            revisions.remove(oldest);
        }
    }

    private static String safeDetail(String detail) {
        Objects.requireNonNull(detail, "detail");
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < detail.length()
                && result.length() < com.matissjurevics.icyou.render.protocol.RenderProtocol
                        .MAX_STATUS_DETAIL; index++) {
            char character = detail.charAt(index);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        return result.toString();
    }
}
