package com.matissjurevics.icyou.render.audio;

import net.minecraft.sound.SoundCategory;

/** Defines the vanilla sound categories and range captured by a camera. */
public final class AudioCapturePolicy {

    private AudioCapturePolicy() {
    }

    public static boolean includes(SoundCategory category) {
        return category != SoundCategory.MUSIC
                && category != SoundCategory.RECORDS
                && category != SoundCategory.VOICE;
    }

    public static double audibleDistance(float volume) {
        return Math.max(16.0, Math.min(AudioSceneProtocol.MAX_VOLUME, volume) * 16.0);
    }

    public static boolean audible(double cameraX, double cameraY, double cameraZ,
                                  AudioSceneProtocol.Event event) {
        double radius = audibleDistance(event.volume());
        double dx = event.x() - cameraX;
        double dy = event.y() - cameraY;
        double dz = event.z() - cameraZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
