package com.matissjurevics.icyou.render.audio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/** Ordered, bounded camera-audio events for one exact remote scene. */
public final class AudioSceneProtocol {

    public static final int VERSION = CameraOverhaulContracts.RENDER_PROTOCOL_VERSION;
    public static final int MAX_EVENTS_PER_BATCH = 256;
    public static final int MAX_SOUND_ID_CHARS = 128;
    public static final float MAX_VOLUME = 16.0f;
    public static final float MAX_PITCH = 4.0f;

    public record Event(Identifier soundId, SoundCategory category,
                        double x, double y, double z, float volume, float pitch,
                        long seed) {
        public Event {
            Objects.requireNonNull(soundId, "soundId");
            Objects.requireNonNull(category, "category");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || soundId.toString().length() > MAX_SOUND_ID_CHARS
                    || !Float.isFinite(volume) || volume <= 0.0f || volume > MAX_VOLUME
                    || !Float.isFinite(pitch) || pitch <= 0.0f || pitch > MAX_PITCH) {
                throw new IllegalArgumentException("Invalid audio scene event");
            }
        }
    }

    public record Batch(UUID jobId, long jobRevision, long snapshotSequence,
                        long batchSequence, long worldTime, boolean truncated,
                        List<Event> events) {
        public Batch {
            Objects.requireNonNull(jobId, "jobId");
            if (jobRevision < 0 || snapshotSequence < 0 || batchSequence < 1) {
                throw new IllegalArgumentException("Invalid audio scene sequence");
            }
            events = List.copyOf(Objects.requireNonNull(events, "events"));
            if (events.isEmpty() || events.size() > MAX_EVENTS_PER_BATCH) {
                throw new IllegalArgumentException("Invalid audio scene batch size");
            }
        }
    }

    private AudioSceneProtocol() {
    }

    public static void write(Batch batch, RegistryByteBuf buffer) {
        buffer.writeVarInt(VERSION);
        buffer.writeUuid(batch.jobId());
        buffer.writeVarLong(batch.jobRevision());
        buffer.writeVarLong(batch.snapshotSequence());
        buffer.writeVarLong(batch.batchSequence());
        buffer.writeLong(batch.worldTime());
        buffer.writeBoolean(batch.truncated());
        buffer.writeVarInt(batch.events().size());
        for (Event event : batch.events()) {
            buffer.writeString(event.soundId().toString(), MAX_SOUND_ID_CHARS);
            buffer.writeByte(event.category().ordinal());
            buffer.writeDouble(event.x());
            buffer.writeDouble(event.y());
            buffer.writeDouble(event.z());
            buffer.writeFloat(event.volume());
            buffer.writeFloat(event.pitch());
            buffer.writeLong(event.seed());
        }
    }

    public static Batch read(RegistryByteBuf buffer) {
        int version = buffer.readVarInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported audio scene version: " + version);
        }
        UUID jobId = buffer.readUuid();
        long revision = buffer.readVarLong();
        long snapshot = buffer.readVarLong();
        long sequence = buffer.readVarLong();
        long worldTime = buffer.readLong();
        boolean truncated = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 1 || count > MAX_EVENTS_PER_BATCH) {
            throw new IllegalArgumentException("Invalid audio scene event count");
        }
        List<Event> events = new java.util.ArrayList<>(count);
        SoundCategory[] categories = SoundCategory.values();
        for (int index = 0; index < count; index++) {
            Identifier soundId = Identifier.tryParse(buffer.readString(MAX_SOUND_ID_CHARS));
            if (soundId == null) {
                throw new IllegalArgumentException("Invalid sound identifier");
            }
            int category = buffer.readUnsignedByte();
            if (category >= categories.length) {
                throw new IllegalArgumentException("Unknown sound category: " + category);
            }
            events.add(new Event(soundId, categories[category], buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readLong()));
        }
        return new Batch(jobId, revision, snapshot, sequence, worldTime, truncated, events);
    }
}
