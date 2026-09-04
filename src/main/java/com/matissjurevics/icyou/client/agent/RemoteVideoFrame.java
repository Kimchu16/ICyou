package com.matissjurevics.icyou.client.agent;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

/** Latest raw offscreen frame for one exact remote render job. */
public record RemoteVideoFrame(UUID jobId, long jobRevision, UUID cameraId,
                               long sequence, long capturedAtMillis, byte[] rgba) {
    public RemoteVideoFrame {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(cameraId, "cameraId");
        if (jobRevision < 0 || sequence < 0 || capturedAtMillis < 0) {
            throw new IllegalArgumentException("Invalid remote frame metadata");
        }
        Objects.requireNonNull(rgba, "rgba");
        int expected = Math.multiplyExact(Math.multiplyExact(
                CameraOverhaulContracts.VIDEO_WIDTH,
                CameraOverhaulContracts.VIDEO_HEIGHT), 4);
        if (rgba.length != expected) {
            throw new IllegalArgumentException("Unexpected remote frame size: " + rgba.length);
        }
        rgba = rgba.clone();
    }

    @Override
    public byte[] rgba() {
        return rgba.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RemoteVideoFrame that && jobId.equals(that.jobId)
                && jobRevision == that.jobRevision && cameraId.equals(that.cameraId)
                && sequence == that.sequence && capturedAtMillis == that.capturedAtMillis
                && Arrays.equals(rgba, that.rgba);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(jobId, jobRevision, cameraId, sequence,
                capturedAtMillis) + Arrays.hashCode(rgba);
    }
}
