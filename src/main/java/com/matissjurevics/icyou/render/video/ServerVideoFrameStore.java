package com.matissjurevics.icyou.render.video;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.render.video.VideoFrameProtocol.Frame;

/** Keeps only the newest authorized JPEG for each exact job and camera. */
public final class ServerVideoFrameStore {

    public record JobKey(UUID jobId, long revision) {
        public JobKey {
            Objects.requireNonNull(jobId, "jobId");
            if (revision < 0) {
                throw new IllegalArgumentException("Invalid job revision");
            }
        }
    }

    public record PublishedFrame(UUID jobId, long jobRevision, UUID cameraId,
                                 long sequence, long capturedAtMillis,
                                 long receivedAtMillis, byte[] jpeg) {
        public PublishedFrame {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(cameraId, "cameraId");
            if (jobRevision < 0 || sequence < 0 || capturedAtMillis < 0
                    || receivedAtMillis < 0) {
                throw new IllegalArgumentException("Invalid published frame metadata");
            }
            jpeg = boundedJpeg(jpeg);
        }

        @Override
        public byte[] jpeg() {
            return jpeg.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PublishedFrame that && jobId.equals(that.jobId)
                    && jobRevision == that.jobRevision && cameraId.equals(that.cameraId)
                    && sequence == that.sequence && capturedAtMillis == that.capturedAtMillis
                    && receivedAtMillis == that.receivedAtMillis
                    && Arrays.equals(jpeg, that.jpeg);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(jobId, jobRevision, cameraId, sequence,
                    capturedAtMillis, receivedAtMillis) + Arrays.hashCode(jpeg);
        }
    }

    private final Map<JobKey, PublishedFrame> byJob = new LinkedHashMap<>();
    private final Map<UUID, JobKey> jobByCamera = new LinkedHashMap<>();

    public synchronized boolean accept(Frame frame, long receivedAtMillis) {
        Objects.requireNonNull(frame, "frame");
        JobKey key = new JobKey(frame.jobId(), frame.jobRevision());
        PublishedFrame previous = byJob.get(key);
        if (previous != null && frame.sequence() <= previous.sequence()) {
            return false;
        }
        JobKey oldCameraJob = jobByCamera.put(frame.cameraId(), key);
        if (oldCameraJob != null && !oldCameraJob.equals(key)) {
            byJob.remove(oldCameraJob);
        }
        byJob.put(key, new PublishedFrame(frame.jobId(), frame.jobRevision(),
                frame.cameraId(), frame.sequence(), frame.capturedAtMillis(),
                receivedAtMillis, frame.jpeg()));
        return true;
    }

    public synchronized Optional<PublishedFrame> latest(UUID cameraId) {
        JobKey key = jobByCamera.get(Objects.requireNonNull(cameraId, "cameraId"));
        return key == null ? Optional.empty() : Optional.ofNullable(byJob.get(key));
    }

    public synchronized void retain(Set<JobKey> activeJobs) {
        Objects.requireNonNull(activeJobs, "activeJobs");
        byJob.keySet().removeIf(key -> !activeJobs.contains(key));
        jobByCamera.entrySet().removeIf(entry -> !byJob.containsKey(entry.getValue()));
    }

    public synchronized int size() {
        return byJob.size();
    }

    public synchronized void clear() {
        byJob.clear();
        jobByCamera.clear();
    }

    private static byte[] boundedJpeg(byte[] jpeg) {
        Objects.requireNonNull(jpeg, "jpeg");
        if (jpeg.length < 4 || jpeg.length > VideoFrameProtocol.MAX_JPEG_BYTES
                || (jpeg[0] & 0xff) != 0xff || (jpeg[1] & 0xff) != 0xd8
                || (jpeg[jpeg.length - 2] & 0xff) != 0xff
                || (jpeg[jpeg.length - 1] & 0xff) != 0xd9) {
            throw new IllegalArgumentException("Invalid bounded JPEG frame");
        }
        return jpeg.clone();
    }
}
