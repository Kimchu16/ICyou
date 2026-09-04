package com.matissjurevics.icyou.render.audio;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/** Bounded per-tick journal of server-authoritative vanilla world sounds. */
public final class AudioSceneJournal {

    public static final int MAX_EVENTS_PER_WORLD_TICK = 4096;

    public record Captured(AudioSceneProtocol.Event event, UUID sourceEntityId) {
        public Captured {
            Objects.requireNonNull(event, "event");
        }
    }

    public record Capture(List<Captured> events, boolean overflowed) {
        public Capture {
            events = List.copyOf(Objects.requireNonNull(events, "events"));
        }
    }

    private static final class MutableCapture {
        private final List<Captured> events = new ArrayList<>();
        private boolean overflowed;
    }

    private static final Map<ServerWorld, MutableCapture> CAPTURES =
            new IdentityHashMap<>();

    private AudioSceneJournal() {
    }

    public static synchronized void record(ServerWorld world, UUID sourceEntityId,
                                           double x, double y, double z,
                                           RegistryEntry<SoundEvent> sound,
                                           SoundCategory category, float volume,
                                           float pitch, long seed) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(sound, "sound");
        if (!AudioCapturePolicy.includes(Objects.requireNonNull(category, "category"))
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(volume) || volume <= 0.0f
                || !Float.isFinite(pitch) || pitch <= 0.0f) {
            return;
        }
        MutableCapture capture = CAPTURES.computeIfAbsent(
                world, ignored -> new MutableCapture());
        if (capture.events.size() >= MAX_EVENTS_PER_WORLD_TICK) {
            capture.overflowed = true;
            return;
        }
        var event = new AudioSceneProtocol.Event(Registries.SOUND_EVENT.getId(sound.value()),
                category, x, y, z, Math.min(volume, AudioSceneProtocol.MAX_VOLUME),
                Math.min(pitch, AudioSceneProtocol.MAX_PITCH), seed);
        capture.events.add(new Captured(event, sourceEntityId));
    }

    public static synchronized Map<ServerWorld, Capture> drain(MinecraftServer server) {
        Map<ServerWorld, Capture> result = new IdentityHashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            MutableCapture capture = CAPTURES.remove(world);
            if (capture != null) {
                result.put(world, new Capture(capture.events, capture.overflowed));
            }
        }
        return Map.copyOf(result);
    }

    public static synchronized void clear(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            CAPTURES.remove(world);
        }
    }
}
