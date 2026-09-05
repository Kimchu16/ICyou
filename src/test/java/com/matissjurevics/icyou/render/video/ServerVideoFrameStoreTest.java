package com.matissjurevics.icyou.render.video;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.video.ServerVideoFrameStore.JobKey;
import com.matissjurevics.icyou.render.video.VideoFrameProtocol.Frame;

class ServerVideoFrameStoreTest {

    private static final byte[] JPEG = {
            (byte) 0xff, (byte) 0xd8, 1, (byte) 0xff, (byte) 0xd9
    };

    @Test
    void keepsOnlyNewestFrameForTheCurrentCameraJob() {
        ServerVideoFrameStore store = new ServerVideoFrameStore();
        UUID camera = UUID.randomUUID();
        UUID firstJob = UUID.randomUUID();
        assertTrue(store.accept(new Frame(firstJob, 0, camera, 1, 2, JPEG), 3));
        assertFalse(store.accept(new Frame(firstJob, 0, camera, 1, 4, JPEG), 5));
        UUID nextJob = UUID.randomUUID();
        assertTrue(store.accept(new Frame(nextJob, 1, camera, 0, 6, JPEG), 7));

        var latest = store.latest(camera).orElseThrow();
        assertEquals(nextJob, latest.jobId());
        assertEquals(1, store.size());
        byte[] returned = latest.jpeg();
        returned[2] = 99;
        assertArrayEquals(JPEG, store.latest(camera).orElseThrow().jpeg());
    }

    @Test
    void removesFramesForJobsTheSchedulerNoLongerOwns() {
        ServerVideoFrameStore store = new ServerVideoFrameStore();
        UUID camera = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        store.accept(new Frame(job, 2, camera, 0, 1, JPEG), 2);

        store.retain(Set.of(new JobKey(job, 2)));
        assertEquals(1, store.size());
        store.retain(Set.of());
        assertEquals(0, store.size());
        assertTrue(store.latest(camera).isEmpty());
    }
}
