package com.matissjurevics.icyou.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.observability.CameraEventCounters.Event;

class CameraEventCountersTest {

    @Test
    void recordsEachOperatorRelevantFailureWithoutIdentifiers() {
        CameraEventCounters counters = new CameraEventCounters();
        counters.record(Event.VIEWER_LIMIT_REJECTION);
        counters.record(Event.WEBRTC_OFFER_REJECTION);
        counters.record(Event.VIDEO_FRAME_REJECTION);
        counters.record(Event.VIDEO_FRAME_REJECTION);
        counters.record(Event.SCENE_FAILURE);

        assertEquals(new CameraEventCounters.Snapshot(1, 1, 2, 1),
                counters.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> new CameraEventCounters.Snapshot(-1, 0, 0, 0));
    }
}
