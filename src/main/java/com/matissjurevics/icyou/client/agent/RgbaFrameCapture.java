package com.matissjurevics.icyou.client.agent;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Converts OpenGL's bottom-up RGBA rows into ordinary top-down frame bytes. */
final class RgbaFrameCapture {

    private RgbaFrameCapture() {
    }

    static byte[] topDown(ByteBuffer bottomUp, int width, int height) {
        Objects.requireNonNull(bottomUp, "bottomUp");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        int rowBytes = Math.multiplyExact(width, 4);
        int totalBytes = Math.multiplyExact(rowBytes, height);
        if (bottomUp.capacity() < totalBytes) {
            throw new IllegalArgumentException("Frame buffer is too small");
        }
        byte[] result = new byte[totalBytes];
        for (int sourceRow = 0; sourceRow < height; sourceRow++) {
            int targetOffset = (height - 1 - sourceRow) * rowBytes;
            int sourceOffset = sourceRow * rowBytes;
            for (int column = 0; column < rowBytes; column++) {
                result[targetOffset + column] = bottomUp.get(sourceOffset + column);
            }
        }
        return result;
    }
}
