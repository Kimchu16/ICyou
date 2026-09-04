package com.matissjurevics.icyou.client.agent;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps only the newest immutable frame for each bounded active job. */
public final class RemoteFrameStore {

    private static final Map<UUID, RemoteVideoFrame> FRAMES = new ConcurrentHashMap<>();

    private RemoteFrameStore() {
    }

    public static void put(RemoteVideoFrame frame) {
        Objects.requireNonNull(frame, "frame");
        FRAMES.compute(frame.jobId(), (jobId, previous) -> previous == null
                || frame.jobRevision() > previous.jobRevision()
                || frame.jobRevision() == previous.jobRevision()
                && frame.sequence() > previous.sequence() ? frame : previous);
    }

    public static RemoteVideoFrame get(UUID jobId) {
        return FRAMES.get(Objects.requireNonNull(jobId, "jobId"));
    }

    public static Map<UUID, RemoteVideoFrame> frames() {
        return Map.copyOf(FRAMES);
    }

    public static void remove(UUID jobId) {
        FRAMES.remove(Objects.requireNonNull(jobId, "jobId"));
    }

    public static void retain(Set<UUID> jobIds) {
        Objects.requireNonNull(jobIds, "jobIds");
        FRAMES.keySet().removeIf(jobId -> !jobIds.contains(jobId));
    }

    public static void clear() {
        FRAMES.clear();
    }
}
