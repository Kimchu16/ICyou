package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class I420FrameConverterTest {

    @Test
    void convertsBlackAndWhiteUsingVideoRange() {
        var black = I420FrameConverter.planes(new byte[] {0, 0, 0, -1}, 1, 1);
        assertArrayEquals(new byte[] {16}, black.y());
        assertArrayEquals(new byte[] {(byte) 128}, black.u());
        assertArrayEquals(new byte[] {(byte) 128}, black.v());

        var white = I420FrameConverter.planes(new byte[] {-1, -1, -1, -1}, 1, 1);
        assertArrayEquals(new byte[] {(byte) 235}, white.y());
        assertArrayEquals(new byte[] {(byte) 128}, white.u());
        assertArrayEquals(new byte[] {(byte) 128}, white.v());
    }

    @Test
    void supportsOddDimensionsAndRejectsWrongByteCounts() {
        var planes = I420FrameConverter.planes(new byte[3 * 3 * 4], 3, 3);
        assertEquals(9, planes.y().length);
        assertEquals(4, planes.u().length);
        assertEquals(4, planes.v().length);
        assertThrows(IllegalArgumentException.class,
                () -> I420FrameConverter.planes(new byte[3], 1, 1));
    }
}
