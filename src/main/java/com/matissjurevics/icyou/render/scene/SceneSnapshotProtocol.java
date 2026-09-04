package com.matissjurevics.icyou.render.scene;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.network.RegistryByteBuf;

/** Versioned, bounded transfer contract for one complete remote scene. */
public final class SceneSnapshotProtocol {

    public static final int VERSION = CameraOverhaulContracts.SCENE_SNAPSHOT_PROTOCOL_VERSION;
    public static final int DIGEST_BYTES = 32;
    public static final int MAX_PART_BYTES = 512 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024;
    public static final int MAX_PARTS = MAX_SNAPSHOT_BYTES / MAX_PART_BYTES;

    private static final int BEGIN = 1;
    private static final int PART = 2;

    public sealed interface Message permits SnapshotBegin, SnapshotPart {
    }

    public record SnapshotBegin(UUID snapshotId, UUID jobId, long jobRevision,
                                long sequence, CameraRef camera, long worldTime,
                                long timeOfDay, float rainGradient,
                                float thunderGradient, int totalBytes,
                                int partCount, byte[] sha256) implements Message {
        public SnapshotBegin {
            Objects.requireNonNull(snapshotId, "snapshotId");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(camera, "camera");
            if (jobRevision < 0 || sequence < 0) {
                throw new IllegalArgumentException("Snapshot revisions cannot be negative");
            }
            requireGradient(rainGradient, "rain");
            requireGradient(thunderGradient, "thunder");
            if (totalBytes < 1 || totalBytes > MAX_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException("Invalid scene snapshot byte count");
            }
            int expectedParts = (totalBytes + MAX_PART_BYTES - 1) / MAX_PART_BYTES;
            if (partCount != expectedParts || partCount < 1 || partCount > MAX_PARTS) {
                throw new IllegalArgumentException("Invalid scene snapshot part count");
            }
            sha256 = requireBytes(sha256, DIGEST_BYTES, "snapshot digest");
        }

        @Override
        public byte[] sha256() {
            return sha256.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SnapshotBegin that
                    && snapshotId.equals(that.snapshotId) && jobId.equals(that.jobId)
                    && jobRevision == that.jobRevision && sequence == that.sequence
                    && camera.equals(that.camera) && worldTime == that.worldTime
                    && timeOfDay == that.timeOfDay
                    && Float.compare(rainGradient, that.rainGradient) == 0
                    && Float.compare(thunderGradient, that.thunderGradient) == 0
                    && totalBytes == that.totalBytes && partCount == that.partCount
                    && Arrays.equals(sha256, that.sha256);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(snapshotId, jobId, jobRevision, sequence, camera,
                    worldTime, timeOfDay, rainGradient, thunderGradient, totalBytes, partCount)
                    + Arrays.hashCode(sha256);
        }
    }

    public record SnapshotPart(UUID snapshotId, int index, byte[] data) implements Message {
        public SnapshotPart {
            Objects.requireNonNull(snapshotId, "snapshotId");
            if (index < 0 || index >= MAX_PARTS) {
                throw new IllegalArgumentException("Invalid scene snapshot part index");
            }
            Objects.requireNonNull(data, "data");
            if (data.length < 1 || data.length > MAX_PART_BYTES) {
                throw new IllegalArgumentException("Invalid scene snapshot part size");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SnapshotPart that && snapshotId.equals(that.snapshotId)
                    && index == that.index && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(snapshotId, index) + Arrays.hashCode(data);
        }
    }

    public record Transfer(SnapshotBegin begin, List<SnapshotPart> parts) {
        public Transfer {
            Objects.requireNonNull(begin, "begin");
            parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
            if (parts.size() != begin.partCount()) {
                throw new IllegalArgumentException("Snapshot transfer is incomplete");
            }
        }
    }

    private SceneSnapshotProtocol() {
    }

    public static Transfer fragment(UUID jobId, long jobRevision, long sequence,
                                    CameraRef camera, long worldTime, long timeOfDay,
                                    float rainGradient, float thunderGradient,
                                    byte[] encodedPackets) {
        Objects.requireNonNull(encodedPackets, "encodedPackets");
        if (encodedPackets.length < 1 || encodedPackets.length > MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Invalid encoded scene size");
        }
        UUID snapshotId = UUID.randomUUID();
        int partCount = (encodedPackets.length + MAX_PART_BYTES - 1) / MAX_PART_BYTES;
        SnapshotBegin begin = new SnapshotBegin(snapshotId, jobId, jobRevision, sequence,
                camera, worldTime, timeOfDay, rainGradient, thunderGradient,
                encodedPackets.length, partCount, sha256(encodedPackets));
        List<SnapshotPart> parts = new ArrayList<>(partCount);
        for (int index = 0; index < partCount; index++) {
            int start = index * MAX_PART_BYTES;
            int end = Math.min(start + MAX_PART_BYTES, encodedPackets.length);
            parts.add(new SnapshotPart(snapshotId, index,
                    Arrays.copyOfRange(encodedPackets, start, end)));
        }
        return new Transfer(begin, parts);
    }

    public static void write(Message message, RegistryByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        buffer.writeVarInt(VERSION);
        switch (message) {
            case SnapshotBegin begin -> {
                buffer.writeByte(BEGIN);
                buffer.writeUuid(begin.snapshotId());
                buffer.writeUuid(begin.jobId());
                buffer.writeVarLong(begin.jobRevision());
                buffer.writeVarLong(begin.sequence());
                CameraRef.PACKET_CODEC.encode(buffer, begin.camera());
                buffer.writeLong(begin.worldTime());
                buffer.writeLong(begin.timeOfDay());
                buffer.writeFloat(begin.rainGradient());
                buffer.writeFloat(begin.thunderGradient());
                buffer.writeVarInt(begin.totalBytes());
                buffer.writeVarInt(begin.partCount());
                buffer.writeByteArray(begin.sha256());
            }
            case SnapshotPart part -> {
                buffer.writeByte(PART);
                buffer.writeUuid(part.snapshotId());
                buffer.writeVarInt(part.index());
                buffer.writeByteArray(part.data());
            }
        }
    }

    public static Message read(RegistryByteBuf buffer) {
        int version = buffer.readVarInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported scene snapshot version: " + version);
        }
        return switch (buffer.readUnsignedByte()) {
            case BEGIN -> new SnapshotBegin(buffer.readUuid(), buffer.readUuid(),
                    buffer.readVarLong(), buffer.readVarLong(),
                    CameraRef.PACKET_CODEC.decode(buffer), buffer.readLong(), buffer.readLong(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readByteArray(DIGEST_BYTES));
            case PART -> new SnapshotPart(buffer.readUuid(), buffer.readVarInt(),
                    buffer.readByteArray(MAX_PART_BYTES));
            default -> throw new IllegalArgumentException("Unknown scene snapshot message");
        };
    }

    public static byte[] sha256(byte[] value) {
        Objects.requireNonNull(value, "value");
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] requireBytes(byte[] value, int size, String label) {
        Objects.requireNonNull(value, label);
        if (value.length != size) {
            throw new IllegalArgumentException(label + " must contain " + size + " bytes");
        }
        return value.clone();
    }

    private static void requireGradient(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException("Invalid " + label + " gradient");
        }
    }
}
