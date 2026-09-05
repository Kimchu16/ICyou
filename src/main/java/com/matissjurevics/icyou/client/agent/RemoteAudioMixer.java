package com.matissjurevics.icyou.client.agent;

import java.util.ArrayList;
import java.util.List;

import com.matissjurevics.icyou.render.audio.AudioCapturePolicy;
import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Event;

/** Mixes bounded decoded vanilla sounds into 48 kHz stereo WebRTC frames. */
final class RemoteAudioMixer {

    static final int SAMPLE_RATE = 48_000;
    static final int CHANNELS = 2;
    static final int FRAMES_10_MS = 480;
    static final int MAX_ACTIVE_CLIPS = 64;

    record Decoded(float[] samples, int channels, int sampleRate,
                   float assetVolume, float assetPitch) {
        Decoded {
            if (samples == null || samples.length == 0 || (channels != 1 && channels != 2)
                    || sampleRate < 8_000 || sampleRate > 192_000
                    || !Float.isFinite(assetVolume) || assetVolume < 0
                    || !Float.isFinite(assetPitch) || assetPitch <= 0) {
                throw new IllegalArgumentException("Invalid decoded sound");
            }
            samples = samples.clone();
        }
    }

    private static final class Clip {
        private final Decoded sound;
        private final double step;
        private final float leftGain;
        private final float rightGain;
        private double position;

        private Clip(Decoded sound, double step, float leftGain, float rightGain) {
            this.sound = sound;
            this.step = step;
            this.leftGain = leftGain;
            this.rightGain = rightGain;
        }
    }

    private final List<Clip> clips = new ArrayList<>();

    synchronized void add(Event event, Decoded sound, double listenerX,
                          double listenerY, double listenerZ,
                          double rightX, double rightZ) {
        if (clips.size() >= MAX_ACTIVE_CLIPS) clips.remove(0);
        double dx = event.x() - listenerX;
        double dy = event.y() - listenerY;
        double dz = event.z() - listenerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double radius = AudioCapturePolicy.audibleDistance(event.volume());
        float attenuation = (float) Math.max(0.0, 1.0 - distance / radius);
        if (attenuation <= 0 || sound.assetVolume() == 0 || event.volume() == 0) {
            return;
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double pan = horizontal == 0 ? 0 : Math.max(-1, Math.min(1,
                (dx * rightX + dz * rightZ) / horizontal));
        float gain = event.volume() * sound.assetVolume() * attenuation;
        float left = (float) (gain * Math.sqrt((1.0 - pan) * 0.5));
        float right = (float) (gain * Math.sqrt((1.0 + pan) * 0.5));
        double step = sound.sampleRate() / (double) SAMPLE_RATE
                * event.pitch() * sound.assetPitch();
        clips.add(new Clip(sound, step, left, right));
    }

    synchronized byte[] mix10ms() {
        float[] mixed = new float[FRAMES_10_MS * CHANNELS];
        for (Clip clip : List.copyOf(clips)) {
            int sourceFrames = clip.sound.samples().length / clip.sound.channels();
            for (int frame = 0; frame < FRAMES_10_MS; frame++) {
                int index = (int) clip.position;
                if (index >= sourceFrames) break;
                int next = Math.min(index + 1, sourceFrames - 1);
                float fraction = (float) (clip.position - index);
                float left = interpolate(clip.sound, index, next, 0, fraction);
                float right = clip.sound.channels() == 1 ? left
                        : interpolate(clip.sound, index, next, 1, fraction);
                mixed[frame * 2] += left * clip.leftGain;
                mixed[frame * 2 + 1] += right * clip.rightGain;
                clip.position += clip.step;
            }
            if (clip.position >= sourceFrames) clips.remove(clip);
        }
        byte[] pcm = new byte[mixed.length * 2];
        for (int index = 0; index < mixed.length; index++) {
            short sample = (short) Math.round(Math.max(-1.0f, Math.min(1.0f,
                    mixed[index])) * Short.MAX_VALUE);
            pcm[index * 2] = (byte) sample;
            pcm[index * 2 + 1] = (byte) (sample >>> 8);
        }
        return pcm;
    }

    synchronized void clear() { clips.clear(); }

    private static float interpolate(Decoded sound, int first, int second,
                                     int channel, float fraction) {
        float a = sound.samples()[first * sound.channels() + channel];
        float b = sound.samples()[second * sound.channels() + channel];
        return a + (b - a) * fraction;
    }
}
