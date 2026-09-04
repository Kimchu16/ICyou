package com.matissjurevics.icyou.render.scene;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.network.RegistryByteBuf;

/** Ordered, bounded live updates following one verified scene snapshot. */
public final class SceneDeltaProtocol {

    public static final int VERSION = CameraOverhaulContracts.SCENE_SNAPSHOT_PROTOCOL_VERSION;
    public static final int MAX_DELTA_BYTES = SceneSnapshotProtocol.MAX_PART_BYTES;

    public record Delta(UUID jobId, long jobRevision, long snapshotSequence,
                        long deltaSequence, long worldTime, long timeOfDay,
                        float rainGradient, float thunderGradient,
                        byte[] encodedPackets) {
        public Delta {
            Objects.requireNonNull(jobId, "jobId");
            if (jobRevision < 0 || snapshotSequence < 0 || deltaSequence < 1) {
                throw new IllegalArgumentException("Invalid scene delta sequence");
            }
            requireGradient(rainGradient, "rain");
            requireGradient(thunderGradient, "thunder");
            Objects.requireNonNull(encodedPackets, "encodedPackets");
            if (encodedPackets.length > MAX_DELTA_BYTES) {
                throw new IllegalArgumentException("Scene delta is too large");
            }
            encodedPackets = encodedPackets.clone();
        }

        @Override
        public byte[] encodedPackets() {
            return encodedPackets.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Delta that && jobId.equals(that.jobId)
                    && jobRevision == that.jobRevision
                    && snapshotSequence == that.snapshotSequence
                    && deltaSequence == that.deltaSequence
                    && worldTime == that.worldTime && timeOfDay == that.timeOfDay
                    && Float.compare(rainGradient, that.rainGradient) == 0
                    && Float.compare(thunderGradient, that.thunderGradient) == 0
                    && Arrays.equals(encodedPackets, that.encodedPackets);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(jobId, jobRevision, snapshotSequence, deltaSequence,
                    worldTime, timeOfDay, rainGradient, thunderGradient)
                    + Arrays.hashCode(encodedPackets);
        }
    }

    private SceneDeltaProtocol() {
    }

    public static void write(Delta delta, RegistryByteBuf buffer) {
        Objects.requireNonNull(delta, "delta");
        buffer.writeVarInt(VERSION);
        buffer.writeUuid(delta.jobId());
        buffer.writeVarLong(delta.jobRevision());
        buffer.writeVarLong(delta.snapshotSequence());
        buffer.writeVarLong(delta.deltaSequence());
        buffer.writeLong(delta.worldTime());
        buffer.writeLong(delta.timeOfDay());
        buffer.writeFloat(delta.rainGradient());
        buffer.writeFloat(delta.thunderGradient());
        buffer.writeByteArray(delta.encodedPackets());
    }

    public static Delta read(RegistryByteBuf buffer) {
        int version = buffer.readVarInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported scene delta version: " + version);
        }
        return new Delta(buffer.readUuid(), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readVarLong(), buffer.readLong(), buffer.readLong(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readByteArray(MAX_DELTA_BYTES));
    }

    private static void requireGradient(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException("Invalid " + label + " gradient");
        }
    }
}
