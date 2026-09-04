package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.client.agent.RemoteAudioSceneStore.InstallResult;
import com.matissjurevics.icyou.render.audio.AudioSceneProtocol;
import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Batch;
import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Event;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

class RemoteAudioSceneStoreTest {

    @Test
    void acceptsOnlyConsecutiveExactSceneBatches() {
        RemoteAudioSceneStore store = new RemoteAudioSceneStore();
        UUID job = UUID.randomUUID();
        assertEquals(InstallResult.GAP, store.install(batch(job, 0, 0, 2, 1)));
        assertEquals(InstallResult.ACCEPTED, store.install(batch(job, 0, 0, 1, 1)));
        assertEquals(InstallResult.STALE, store.install(batch(job, 0, 0, 1, 1)));
        assertEquals(InstallResult.GAP, store.install(batch(job, 0, 0, 3, 1)));
        assertEquals(InstallResult.MISMATCH, store.install(batch(job, 1, 0, 2, 1)));
        assertEquals(1, store.drain(job).size());
    }

    @Test
    void boundsPendingEventsAndCleansUpInactiveJobs() {
        RemoteAudioSceneStore store = new RemoteAudioSceneStore();
        UUID job = UUID.randomUUID();
        int remaining = RemoteAudioSceneStore.MAX_PENDING_EVENTS_PER_JOB + 1;
        long sequence = 1;
        InstallResult last = null;
        while (remaining > 0) {
            int count = Math.min(remaining, AudioSceneProtocol.MAX_EVENTS_PER_BATCH);
            last = store.install(batch(job, 0, 0, sequence++, count));
            remaining -= count;
        }
        assertEquals(InstallResult.ACCEPTED_TRUNCATED, last);
        assertEquals(RemoteAudioSceneStore.MAX_PENDING_EVENTS_PER_JOB, store.pending(job));
        store.retain(Set.of());
        assertEquals(0, store.pending(job));
    }

    private static Batch batch(UUID job, long revision, long snapshot,
                               long sequence, int count) {
        List<Event> events = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            events.add(new Event(Identifier.of("minecraft", "test"),
                    SoundCategory.BLOCKS, index, 0, 0, 1, 1, index));
        }
        return new Batch(job, revision, snapshot, sequence, 0, false, events);
    }
}
