package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.video.VideoFrameProtocol;

class JpegFrameEncoderTest {

    @Test
    void encodesAFullRgbaFrameAsABoundedJpeg() {
        int bytes = CameraOverhaulContracts.VIDEO_WIDTH
                * CameraOverhaulContracts.VIDEO_HEIGHT * 4;
        RemoteVideoFrame frame = new RemoteVideoFrame(UUID.randomUUID(), 0,
                UUID.randomUUID(), 0, 1, new byte[bytes]);

        byte[] jpeg = JpegFrameEncoder.encode(frame);

        assertTrue(jpeg.length <= VideoFrameProtocol.MAX_JPEG_BYTES);
        assertEquals(0xff, jpeg[0] & 0xff);
        assertEquals(0xd8, jpeg[1] & 0xff);
        assertEquals(0xff, jpeg[jpeg.length - 2] & 0xff);
        assertEquals(0xd9, jpeg[jpeg.length - 1] & 0xff);
    }
}
