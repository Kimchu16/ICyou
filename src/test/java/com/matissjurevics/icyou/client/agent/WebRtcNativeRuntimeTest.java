package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.media.SyncClock;
import dev.onvoid.webrtc.media.audio.CustomAudioSource;
import dev.onvoid.webrtc.media.video.CustomVideoSource;

class WebRtcNativeRuntimeTest {

    @Test
    @EnabledIfSystemProperty(named = "icyou.nativeSmoke", matches = "true")
    void createsBundledFactoryAndSynchronizedSources() {
        PeerConnectionFactory factory = new PeerConnectionFactory();
        SyncClock clock = new SyncClock();
        CustomVideoSource video = new CustomVideoSource(clock);
        CustomAudioSource audio = new CustomAudioSource(clock);
        try {
            assertTrue(clock.getTimestampUs() > 0);
        } finally {
            audio.dispose();
            video.dispose();
            clock.dispose();
            factory.dispose();
        }
    }
}
