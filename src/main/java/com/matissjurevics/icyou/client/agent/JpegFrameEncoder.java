package com.matissjurevics.icyou.client.agent;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.video.VideoFrameProtocol;

import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.stb.STBIWriteCallbackI;
import org.lwjgl.system.MemoryUtil;

/** Bounded in-memory JPEG encoding for raw remote RGBA frames. */
final class JpegFrameEncoder {

    private JpegFrameEncoder() {
    }

    static byte[] encode(RemoteVideoFrame frame) {
        byte[] rgba = frame.rgba();
        ByteBuffer input = ByteBuffer.allocateDirect(rgba.length);
        input.put(rgba).flip();
        ByteArrayOutputStream output = new ByteArrayOutputStream(128 * 1024);
        boolean[] overflowed = new boolean[1];
        STBIWriteCallbackI callback = (context, data, size) -> {
            if (overflowed[0]
                    || (long) output.size() + size > VideoFrameProtocol.MAX_JPEG_BYTES) {
                overflowed[0] = true;
                return;
            }
            ByteBuffer chunk = MemoryUtil.memByteBuffer(data, size);
            byte[] copy = new byte[size];
            chunk.get(copy);
            output.writeBytes(copy);
        };
        int result = STBImageWrite.stbi_write_jpg_to_func(callback, 0L,
                CameraOverhaulContracts.VIDEO_WIDTH,
                CameraOverhaulContracts.VIDEO_HEIGHT, 4, input,
                CameraOverhaulContracts.JPEG_QUALITY);
        byte[] jpeg = output.toByteArray();
        if (result == 0 || overflowed[0] || jpeg.length < 4) {
            throw new IllegalStateException("JPEG encoding failed or exceeded its limit");
        }
        return jpeg;
    }
}
