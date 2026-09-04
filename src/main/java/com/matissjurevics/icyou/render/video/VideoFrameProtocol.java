package com.matissjurevics.icyou.render.video;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.network.RegistryByteBuf;

/** Bounded render-agent to server JPEG frame contract. */
public final class VideoFrameProtocol {

    public static final int VERSION = CameraOverhaulContracts.RENDER_PROTOCOL_VERSION;
    public static final int MAX_JPEG_BYTES = 2 * 1024 * 1024;

    public record Frame(UUID jobId, long jobRevision, UUID cameraId, long sequence,
                        long capturedAtMillis, byte[] jpeg) {
        public Frame {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(cameraId, "cameraId");
            if (jobRevision < 0 || sequence < 0 || capturedAtMillis < 0) {
                throw new IllegalArgumentException("Invalid video frame metadata");
            }
            Objects.requireNonNull(jpeg, "jpeg");
            if (jpeg.length < 4 || jpeg.length > MAX_JPEG_BYTES
                    || (jpeg[0] & 0xff) != 0xff || (jpeg[1] & 0xff) != 0xd8
                    || (jpeg[jpeg.length - 2] & 0xff) != 0xff
                    || (jpeg[jpeg.length - 1] & 0xff) != 0xd9) {
                throw new IllegalArgumentException("Invalid bounded JPEG frame");
            }
            jpeg = jpeg.clone();
        }

        @Override
        public byte[] jpeg() {
            return jpeg.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Frame that && jobId.equals(that.jobId)
                    && jobRevision == that.jobRevision && cameraId.equals(that.cameraId)
                    && sequence == that.sequence && capturedAtMillis == that.capturedAtMillis
                    && Arrays.equals(jpeg, that.jpeg);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(jobId, jobRevision, cameraId, sequence,
                    capturedAtMillis) + Arrays.hashCode(jpeg);
        }
    }

    private VideoFrameProtocol() {
    }

    public static void write(Frame frame, RegistryByteBuf buffer) {
        Objects.requireNonNull(frame, "frame");
        buffer.writeVarInt(VERSION);
        buffer.writeUuid(frame.jobId());
        buffer.writeVarLong(frame.jobRevision());
        buffer.writeUuid(frame.cameraId());
        buffer.writeVarLong(frame.sequence());
        buffer.writeVarLong(frame.capturedAtMillis());
        buffer.writeByteArray(frame.jpeg());
    }

    public static Frame read(RegistryByteBuf buffer) {
        int version = buffer.readVarInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported video frame version: " + version);
        }
        return new Frame(buffer.readUuid(), buffer.readVarLong(), buffer.readUuid(),
                buffer.readVarLong(), buffer.readVarLong(),
                buffer.readByteArray(MAX_JPEG_BYTES));
    }
}
