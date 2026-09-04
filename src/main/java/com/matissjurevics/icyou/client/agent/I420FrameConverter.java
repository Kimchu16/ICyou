package com.matissjurevics.icyou.client.agent;

import java.nio.ByteBuffer;

import dev.onvoid.webrtc.media.video.NativeI420Buffer;
import dev.onvoid.webrtc.media.video.VideoFrame;

/** Converts top-down RGBA camera pixels into WebRTC's I420 layout. */
final class I420FrameConverter {

    record Planes(byte[] y, byte[] u, byte[] v) {}

    private I420FrameConverter() {}

    static VideoFrame convert(RemoteVideoFrame frame, int width, int height,
                              long timestampNanos) {
        if (timestampNanos < 0) {
            throw new IllegalArgumentException("Invalid video timestamp");
        }
        Planes planes = planes(frame.rgba(), width, height);
        NativeI420Buffer buffer = NativeI420Buffer.allocate(width, height);
        copyRows(planes.y(), buffer.getDataY(), width, height, buffer.getStrideY());
        copyRows(planes.u(), buffer.getDataU(), (width + 1) / 2,
                (height + 1) / 2, buffer.getStrideU());
        copyRows(planes.v(), buffer.getDataV(), (width + 1) / 2,
                (height + 1) / 2, buffer.getStrideV());
        return new VideoFrame(buffer, timestampNanos);
    }

    static Planes planes(byte[] rgba, int width, int height) {
        if (width < 1 || height < 1 || rgba.length != width * height * 4) {
            throw new IllegalArgumentException("Invalid RGBA frame dimensions");
        }
        byte[] yPlane = new byte[width * height];
        int chromaWidth = (width + 1) / 2;
        int chromaHeight = (height + 1) / 2;
        byte[] uPlane = new byte[chromaWidth * chromaHeight];
        byte[] vPlane = new byte[chromaWidth * chromaHeight];
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int offset = (py * width + px) * 4;
                int r = rgba[offset] & 0xff;
                int g = rgba[offset + 1] & 0xff;
                int b = rgba[offset + 2] & 0xff;
                yPlane[py * width + px] = (byte) clamp(
                        ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
            }
        }
        for (int py = 0; py < height; py += 2) {
            for (int px = 0; px < width; px += 2) {
                int r = 0, g = 0, b = 0, count = 0;
                for (int dy = 0; dy < 2 && py + dy < height; dy++) {
                    for (int dx = 0; dx < 2 && px + dx < width; dx++) {
                        int offset = ((py + dy) * width + px + dx) * 4;
                        r += rgba[offset] & 0xff;
                        g += rgba[offset + 1] & 0xff;
                        b += rgba[offset + 2] & 0xff;
                        count++;
                    }
                }
                r /= count; g /= count; b /= count;
                int index = (py / 2) * chromaWidth + px / 2;
                uPlane[index] = (byte) clamp(
                        ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                vPlane[index] = (byte) clamp(
                        ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
            }
        }
        return new Planes(yPlane, uPlane, vPlane);
    }

    private static void copyRows(byte[] source, ByteBuffer target, int width,
                                 int height, int stride) {
        for (int row = 0; row < height; row++) {
            int sourceOffset = row * width;
            int targetOffset = row * stride;
            for (int column = 0; column < width; column++) {
                target.put(targetOffset + column, source[sourceOffset + column]);
            }
        }
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
