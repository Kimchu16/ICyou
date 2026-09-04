package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Event;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

class RemoteAudioMixerTest {

    @Test
    void producesCenteredStereoPcm() {
        RemoteAudioMixer mixer = new RemoteAudioMixer();
        mixer.add(event(0, 0, 0, 1), decoded(0.5f), 0, 0, 0, 1, 0);
        byte[] pcm = mixer.mix10ms();
        assertEquals(RemoteAudioMixer.FRAMES_10_MS * 4, pcm.length);
        assertEquals(sample(pcm, 0), sample(pcm, 1));
        assertTrue(sample(pcm, 0) > 0);
    }

    @Test
    void pansAndAttenuatesByCameraPosition() {
        RemoteAudioMixer right = new RemoteAudioMixer();
        right.add(event(1, 0, 0, 1), decoded(0.5f), 0, 0, 0, 1, 0);
        byte[] pcm = right.mix10ms();
        assertEquals(0, sample(pcm, 0));
        assertTrue(sample(pcm, 1) > 0);

        RemoteAudioMixer outOfRange = new RemoteAudioMixer();
        outOfRange.add(event(16, 0, 0, 1), decoded(0.5f), 0, 0, 0, 1, 0);
        assertTrue(Arrays.equals(new byte[RemoteAudioMixer.FRAMES_10_MS * 4],
                outOfRange.mix10ms()));
    }

    private static Event event(double x, double y, double z, float volume) {
        return new Event(Identifier.of("minecraft", "test"), SoundCategory.BLOCKS,
                x, y, z, volume, 1, 1);
    }

    private static RemoteAudioMixer.Decoded decoded(float value) {
        float[] samples = new float[RemoteAudioMixer.FRAMES_10_MS];
        Arrays.fill(samples, value);
        return new RemoteAudioMixer.Decoded(samples, 1,
                RemoteAudioMixer.SAMPLE_RATE, 1, 1);
    }

    private static short sample(byte[] pcm, int channel) {
        int offset = channel * 2;
        return (short) ((pcm[offset] & 0xff) | pcm[offset + 1] << 8);
    }
}
