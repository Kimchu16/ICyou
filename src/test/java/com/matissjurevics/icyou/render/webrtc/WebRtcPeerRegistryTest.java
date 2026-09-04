package com.matissjurevics.icyou.render.webrtc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WebRtcPeerRegistryTest {

    @Test
    void bindsAnswersAndPollsToExactParticipants() {
        WebRtcPeerRegistry registry = new WebRtcPeerRegistry();
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        UUID viewer = UUID.randomUUID();
        UUID camera = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID agent = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        var opened = registry.open(viewer, camera, job, 7, agent, session,
                "v=0\r\no=offer", now).orElseThrow();

        assertFalse(registry.answer(opened.binding().peerId(), job, 8, agent,
                session, "v=0\r\no=wrong"));
        assertTrue(registry.answer(opened.binding().peerId(), job, 7, agent,
                session, "v=0\r\no=answer"));
        assertFalse(registry.answer(opened.binding().peerId(), job, 7, agent,
                session, "v=0\r\no=replacement"));
        assertTrue(registry.poll(opened.binding().peerId(), UUID.randomUUID(),
                camera, now).isEmpty());
        assertEquals("v=0\r\no=answer", registry.poll(opened.binding().peerId(),
                viewer, camera, now.plusSeconds(1)).orElseThrow()
                .answerSdp().orElseThrow());
    }

    @Test
    void expiresAndBoundsTransientPeers() {
        WebRtcPeerRegistry registry = new WebRtcPeerRegistry();
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        for (int index = 0; index < WebRtcPeerRegistry.MAX_PEERS; index++) {
            assertTrue(open(registry, now).isPresent());
        }
        assertTrue(open(registry, now).isEmpty());
        assertEquals(WebRtcPeerRegistry.MAX_PEERS,
                registry.expire(now.plus(WebRtcPeerRegistry.PEER_TIMEOUT)).size());
        assertEquals(0, registry.size());
    }

    private static java.util.Optional<WebRtcPeerRegistry.Opened> open(
            WebRtcPeerRegistry registry, Instant now) {
        return registry.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, UUID.randomUUID(), UUID.randomUUID(), "v=0\r\n", now);
    }
}
