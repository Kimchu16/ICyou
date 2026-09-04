package com.matissjurevics.icyou.client.agent;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Selects the oldest due render job so one game frame does bounded work. */
final class RemoteRenderCadence {

    record Candidate(UUID jobId, long lastAttemptNanos) {
        Candidate {
            Objects.requireNonNull(jobId, "jobId");
        }
    }

    private RemoteRenderCadence() {
    }

    static Optional<UUID> select(Collection<Candidate> candidates, long nowNanos,
                                 long intervalNanos) {
        Objects.requireNonNull(candidates, "candidates");
        if (intervalNanos < 1) {
            throw new IllegalArgumentException("Render interval must be positive");
        }
        return candidates.stream().filter(candidate -> candidate.lastAttemptNanos()
                        == Long.MIN_VALUE || nowNanos - candidate.lastAttemptNanos()
                        >= intervalNanos)
                .min(Comparator.comparingLong(Candidate::lastAttemptNanos)
                        .thenComparing(candidate -> candidate.jobId().toString()))
                .map(Candidate::jobId);
    }
}
