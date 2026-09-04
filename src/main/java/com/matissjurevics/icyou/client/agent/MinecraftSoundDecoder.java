package com.matissjurevics.icyou.client.agent;

import java.io.IOException;

import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Event;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** Resolves seeded vanilla sound definitions and decodes their OGG resource. */
final class MinecraftSoundDecoder {

    record Resolved(Identifier resource, float volume, float pitch) {}

    private MinecraftSoundDecoder() {}

    static Resolved resolve(MinecraftClient client, Event event) {
        var set = client.getSoundManager().get(event.soundId());
        if (set == null) return null;
        Random random = Random.create(event.seed());
        Sound sound = set.getSound(random);
        if (sound == null || sound == SoundManagerMissingHolder.MISSING) return null;
        return new Resolved(sound.getLocation(), sound.getVolume().get(random),
                sound.getPitch().get(random));
    }

    static RemoteAudioMixer.Decoded decode(MinecraftClient client, Resolved sound)
            throws IOException {
        try (var input = client.getResourceManager().open(sound.resource());
             var ogg = new OggAudioStream(input)) {
            FloatArrayList samples = new FloatArrayList();
            while (ogg.read(samples::add)) {
                if (samples.size() > RemoteAudioMixer.SAMPLE_RATE * 2 * 60) {
                    throw new IOException("Remote sound exceeds 60 seconds");
                }
            }
            var format = ogg.getFormat();
            return new RemoteAudioMixer.Decoded(samples.toFloatArray(),
                    format.getChannels(), Math.round(format.getSampleRate()),
                    sound.volume(), sound.pitch());
        }
    }

    /** Avoids importing a mutable public singleton through every test seam. */
    private static final class SoundManagerMissingHolder {
        private static final Sound MISSING = net.minecraft.client.sound.SoundManager.MISSING_SOUND;
    }
}
