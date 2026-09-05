package com.matissjurevics.icyou.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraSystemStatusTest {

    private static final CameraEventCounters.Snapshot NO_EVENTS =
            new CameraEventCounters.Snapshot(0, 0, 0, 0);

    @Test
    void classifiesIdleRunningAndDegradedStates() {
        assertEquals(CameraSystemStatus.State.IDLE,
                status(0, 0, 0, 0).state());
        assertEquals(CameraSystemStatus.State.RUNNING,
                status(1, 0, 0, 1).state());
        assertEquals(CameraSystemStatus.State.DEGRADED,
                status(0, 1, 0, 0).state());
    }

    @Test
    void producesShortReadableCountOnlyLines() {
        CameraSystemStatus status = new CameraSystemStatus(
                10, 64, 1, 2, 0, 1, 3, 16,
                2, 3, 2, 27, 2, 3, true,
                new CameraEventCounters.Snapshot(4, 5, 6, 7));

        assertEquals(5, status.lines().size());
        assertEquals("ICyou status: running.", status.lines().getFirst());
        assertTrue(status.lines().get(1).contains("10/64 registered"));
        assertTrue(status.lines().get(4).contains("4 viewer-limit rejects"));
    }

    @Test
    void validatesCountsButAllowsPreservedCamerasAboveNewLimit() {
        assertEquals(65, new CameraSystemStatus(
                65, 64, 0, 0, 0, 0, 0, 16,
                0, 0, 0, 0, 0, 0, false, NO_EVENTS).cameras());
        assertThrows(IllegalArgumentException.class, () -> new CameraSystemStatus(
                0, 64, 0, 0, 0, 0, 17, 16,
                0, 0, 0, 0, 0, 0, false, NO_EVENTS));
        assertThrows(IllegalArgumentException.class, () -> new CameraSystemStatus(
                0, 64, 0, 0, 0, 0, 0, 16,
                0, 1, 2, 0, 0, 0, false, NO_EVENTS));
    }

    private static CameraSystemStatus status(int available, int unavailable,
                                              int retaining, int viewers) {
        return new CameraSystemStatus(
                1, 64, 0, available, unavailable, retaining, viewers, 16,
                1, available, available, 0, 0, 0, true, NO_EVENTS);
    }
}
