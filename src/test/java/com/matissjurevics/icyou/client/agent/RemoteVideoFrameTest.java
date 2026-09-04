package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

class RemoteVideoFrameTest {

    private static final int FRAME_BYTES = CameraOverhaulContracts.VIDEO_WIDTH
            * CameraOverhaulContracts.VIDEO_HEIGHT * 4;

    @AfterEach
    void clearStore() {
        RemoteFrameStore.clear();
    }

    @Test
    void frameBytesAreExactAndDefensivelyCopied() {
        byte[] rgba = new byte[FRAME_BYTES];
        rgba[0] = 3;
        RemoteVideoFrame frame = new RemoteVideoFrame(UUID.randomUUID(), 2,
                UUID.randomUUID(), 4, 10, rgba);
        rgba[0] = 9;

        assertEquals(3, frame.rgba()[0]);
        assertNotSame(frame.rgba(), frame.rgba());
        assertThrows(IllegalArgumentException.class, () -> new RemoteVideoFrame(
                UUID.randomUUID(), 0, UUID.randomUUID(), 0, 0, new byte[4]));
    }

    @Test
    void storeKeepsOnlyTheNewestJobFrameAndCleansUp() {
        UUID jobId = UUID.randomUUID();
        UUID cameraId = UUID.randomUUID();
        RemoteVideoFrame first = frame(jobId, cameraId, 1, 1, (byte) 1);
        RemoteVideoFrame stale = frame(jobId, cameraId, 1, 0, (byte) 2);
        RemoteVideoFrame newest = frame(jobId, cameraId, 2, 0, (byte) 3);

        RemoteFrameStore.put(first);
        RemoteFrameStore.put(stale);
        assertArrayEquals(first.rgba(), RemoteFrameStore.get(jobId).rgba());
        RemoteFrameStore.put(newest);
        assertArrayEquals(newest.rgba(), RemoteFrameStore.get(jobId).rgba());

        RemoteFrameStore.retain(java.util.Set.of());
        assertNull(RemoteFrameStore.get(jobId));
    }

    private static RemoteVideoFrame frame(UUID jobId, UUID cameraId, long revision,
                                          long sequence, byte value) {
        byte[] rgba = new byte[FRAME_BYTES];
        rgba[0] = value;
        return new RemoteVideoFrame(jobId, revision, cameraId, sequence, 10, rgba);
    }
}
