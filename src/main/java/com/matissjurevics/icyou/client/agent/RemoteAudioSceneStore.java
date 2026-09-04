package com.matissjurevics.icyou.client.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Batch;
import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Event;

/** Bounded ordered audio-event handoff for PR 23's remote mixer. */
public final class RemoteAudioSceneStore {

    public static final int MAX_PENDING_EVENTS_PER_JOB = 512;

    public enum InstallResult {
        ACCEPTED,
        ACCEPTED_TRUNCATED,
        STALE,
        GAP,
        MISMATCH
    }

    private static final class State {
        private final long revision;
        private final long snapshot;
        private final Deque<Event> events = new ArrayDeque<>();
        private long nextBatch = 1;
        private boolean dropped;

        private State(long revision, long snapshot) {
            this.revision = revision;
            this.snapshot = snapshot;
        }
    }

    private final Map<UUID, State> states = new LinkedHashMap<>();

    public synchronized InstallResult install(Batch batch) {
        Objects.requireNonNull(batch, "batch");
        State state = states.get(batch.jobId());
        if (state == null || state.revision != batch.jobRevision()
                || state.snapshot != batch.snapshotSequence()) {
            if (batch.batchSequence() != 1) {
                return state == null ? InstallResult.GAP : InstallResult.MISMATCH;
            }
            state = new State(batch.jobRevision(), batch.snapshotSequence());
            states.put(batch.jobId(), state);
        }
        if (batch.batchSequence() < state.nextBatch) {
            return InstallResult.STALE;
        }
        if (batch.batchSequence() > state.nextBatch) {
            return InstallResult.GAP;
        }
        state.nextBatch++;
        for (Event event : batch.events()) {
            while (state.events.size() >= MAX_PENDING_EVENTS_PER_JOB) {
                state.events.removeFirst();
                state.dropped = true;
            }
            state.events.addLast(event);
        }
        return batch.truncated() || state.dropped
                ? InstallResult.ACCEPTED_TRUNCATED : InstallResult.ACCEPTED;
    }

    public synchronized List<Event> drain(UUID jobId) {
        State state = states.get(Objects.requireNonNull(jobId, "jobId"));
        if (state == null || state.events.isEmpty()) {
            return List.of();
        }
        List<Event> result = new ArrayList<>(state.events);
        state.events.clear();
        state.dropped = false;
        return List.copyOf(result);
    }

    public synchronized void retain(Set<UUID> activeJobs) {
        states.keySet().retainAll(Objects.requireNonNull(activeJobs, "activeJobs"));
    }

    public synchronized int pending(UUID jobId) {
        State state = states.get(Objects.requireNonNull(jobId, "jobId"));
        return state == null ? 0 : state.events.size();
    }

    public synchronized void clear() {
        states.clear();
    }
}
