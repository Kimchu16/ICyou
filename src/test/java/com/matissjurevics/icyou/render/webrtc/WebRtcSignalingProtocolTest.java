package com.matissjurevics.icyou.render.webrtc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.webrtc.WebRtcSignalingProtocol.Answer;
import com.matissjurevics.icyou.render.webrtc.WebRtcSignalingProtocol.Close;
import com.matissjurevics.icyou.render.webrtc.WebRtcSignalingProtocol.Offer;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;

class WebRtcSignalingProtocolTest {

    @Test
    void everyMessageRoundTrips() {
        UUID peer = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        Offer offer = new Offer(peer, job, 4, UUID.randomUUID(), "v=0\r\no=offer");
        Answer answer = new Answer(peer, job, 4, "v=0\r\no=answer");
        Close close = new Close(peer, job, 4);
        RegistryByteBuf buffer = buffer();
        try {
            WebRtcOfferS2CPayload.CODEC.encode(buffer, new WebRtcOfferS2CPayload(offer));
            assertEquals(offer, WebRtcOfferS2CPayload.CODEC.decode(buffer).offer());
            buffer.clear();
            WebRtcAnswerC2SPayload.CODEC.encode(buffer, new WebRtcAnswerC2SPayload(answer));
            assertEquals(answer, WebRtcAnswerC2SPayload.CODEC.decode(buffer).answer());
            buffer.clear();
            WebRtcCloseS2CPayload.CODEC.encode(buffer, new WebRtcCloseS2CPayload(close));
            assertEquals(close, WebRtcCloseS2CPayload.CODEC.decode(buffer).close());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsBadVersionsAndSdp() {
        assertThrows(IllegalArgumentException.class, () -> new Offer(
                UUID.randomUUID(), UUID.randomUUID(), 0, UUID.randomUUID(), " "));
        assertThrows(IllegalArgumentException.class, () -> new Answer(
                UUID.randomUUID(), UUID.randomUUID(), 0, "x".repeat(
                        WebRtcSignalingProtocol.MAX_SDP_CHARS + 1)));
        RegistryByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(WebRtcSignalingProtocol.VERSION + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> WebRtcSignalingProtocol.readClose(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RegistryByteBuf buffer() {
        return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
    }
}
