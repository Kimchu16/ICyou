package com.matissjurevics.icyou.render.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.audio.AudioSceneJournal.Capture;
import com.matissjurevics.icyou.render.audio.AudioSceneJournal.Captured;
import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Event;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

class AudioSceneSelectorTest {

    @Test
    void policyIncludesWorldSoundsButExcludesMusicRecordsAndVoice() {
        assertTrue(AudioCapturePolicy.includes(SoundCategory.WEATHER));
        assertTrue(AudioCapturePolicy.includes(SoundCategory.BLOCKS));
        assertTrue(AudioCapturePolicy.includes(SoundCategory.HOSTILE));
        assertTrue(AudioCapturePolicy.includes(SoundCategory.PLAYERS));
        assertFalse(AudioCapturePolicy.includes(SoundCategory.MUSIC));
        assertFalse(AudioCapturePolicy.includes(SoundCategory.RECORDS));
        assertFalse(AudioCapturePolicy.includes(SoundCategory.VOICE));
    }

    @Test
    void selectsOnlyAudibleNonAgentSourcesAndReportsTruncation() {
        UUID agent = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        List<Captured> sounds = new ArrayList<>();
        sounds.add(new Captured(event(0, 0, 0), null));
        sounds.add(new Captured(event(1, 0, 0), player));
        sounds.add(new Captured(event(2, 0, 0), agent));
        sounds.add(new Captured(event(100, 0, 0), null));
        var selected = AudioSceneSelector.select(new Capture(sounds, false),
                0, 0, 0, agent::equals);
        assertEquals(2, selected.events().size());
        assertFalse(selected.truncated());

        List<Captured> many = new ArrayList<>();
        for (int index = 0; index <= AudioSceneProtocol.MAX_EVENTS_PER_BATCH; index++) {
            many.add(new Captured(event(0, 0, 0), null));
        }
        selected = AudioSceneSelector.select(new Capture(many, false),
                0, 0, 0, ignored -> false);
        assertEquals(AudioSceneProtocol.MAX_EVENTS_PER_BATCH, selected.events().size());
        assertTrue(selected.truncated());
    }

    private static Event event(double x, double y, double z) {
        return new Event(Identifier.of("minecraft", "test"), SoundCategory.AMBIENT,
                x, y, z, 1.0f, 1.0f, 1);
    }
}
