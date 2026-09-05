package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class RgbaFrameCaptureTest {

    @Test
    void flipsOpenGlRowsWithoutChangingPixels() {
        ByteBuffer source = ByteBuffer.allocateDirect(16);
        for (int index = 0; index < 16; index++) {
            source.put(index, (byte) index);
        }

        assertArrayEquals(new byte[] {8, 9, 10, 11, 12, 13, 14, 15,
                0, 1, 2, 3, 4, 5, 6, 7},
                RgbaFrameCapture.topDown(source, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> RgbaFrameCapture.topDown(ByteBuffer.allocate(3), 1, 1));
    }
}
