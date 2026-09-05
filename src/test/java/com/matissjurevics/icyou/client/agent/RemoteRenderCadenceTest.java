package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RemoteRenderCadenceTest {

    @Test
    void selectsTheOldestDueJobAndTreatsNewJobsAsDue() {
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        assertEquals(second, RemoteRenderCadence.select(List.of(
                new RemoteRenderCadence.Candidate(first, 20),
                new RemoteRenderCadence.Candidate(second, 10)),
                100, 50).orElseThrow());

        assertEquals(first, RemoteRenderCadence.select(List.of(
                new RemoteRenderCadence.Candidate(first, Long.MIN_VALUE),
                new RemoteRenderCadence.Candidate(second, 10)),
                100, 50).orElseThrow());
    }

    @Test
    void skipsJobsUntilTheirIntervalAndRejectsInvalidIntervals() {
        UUID jobId = UUID.randomUUID();
        assertTrue(RemoteRenderCadence.select(List.of(
                new RemoteRenderCadence.Candidate(jobId, 80)),
                100, 50).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RemoteRenderCadence.select(
                List.of(), 0, 0));
    }
}
