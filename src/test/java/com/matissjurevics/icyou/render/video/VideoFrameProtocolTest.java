package com.matissjurevics.icyou.render.video;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.video.VideoFrameProtocol.Frame;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;

class VideoFrameProtocolTest {

    private static final byte[] JPEG = {
            (byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9
    };

    @Test
    void payloadRoundTripsAndDefensivelyCopiesJpeg() {
        byte[] source = JPEG.clone();
        Frame frame = new Frame(UUID.randomUUID(), 2, UUID.randomUUID(), 3, 4, source);
        source[2] = 99;
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            VideoFrameC2SPayload.CODEC.encode(buffer, new VideoFrameC2SPayload(frame));
            Frame decoded = VideoFrameC2SPayload.CODEC.decode(buffer).frame();
            assertEquals(frame, decoded);
            assertArrayEquals(JPEG, decoded.jpeg());
            assertNotSame(decoded.jpeg(), decoded.jpeg());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsInvalidMetadataAndUnboundedOrNonJpegBodies() {
        assertThrows(IllegalArgumentException.class, () -> new Frame(
                UUID.randomUUID(), -1, UUID.randomUUID(), 0, 0, JPEG));
        assertThrows(IllegalArgumentException.class, () -> new Frame(
                UUID.randomUUID(), 0, UUID.randomUUID(), 0, 0, new byte[3]));
        byte[] oversized = new byte[VideoFrameProtocol.MAX_JPEG_BYTES + 1];
        oversized[0] = (byte) 0xff;
        oversized[1] = (byte) 0xd8;
        oversized[oversized.length - 2] = (byte) 0xff;
        oversized[oversized.length - 1] = (byte) 0xd9;
        assertThrows(IllegalArgumentException.class, () -> new Frame(
                UUID.randomUUID(), 0, UUID.randomUUID(), 0, 0, oversized));
    }

    @Test
    void rejectsUnknownWireVersion() {
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            buffer.writeVarInt(VideoFrameProtocol.VERSION + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> VideoFrameProtocol.read(buffer));
        } finally {
            buffer.release();
        }
    }
}
