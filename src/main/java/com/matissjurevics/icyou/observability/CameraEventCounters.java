package com.matissjurevics.icyou.observability;

/** Small monotonic counters for operator-relevant camera failures. */
public final class CameraEventCounters {

    public enum Event {
        VIEWER_LIMIT_REJECTION,
        WEBRTC_OFFER_REJECTION,
        VIDEO_FRAME_REJECTION,
        SCENE_FAILURE
    }

    public record Snapshot(long viewerLimitRejections, long webRtcOfferRejections,
                           long videoFrameRejections, long sceneFailures) {
        public Snapshot {
            if (viewerLimitRejections < 0 || webRtcOfferRejections < 0
                    || videoFrameRejections < 0 || sceneFailures < 0) {
                throw new IllegalArgumentException("Camera event counters cannot be negative");
            }
        }
    }

    private long viewerLimitRejections;
    private long webRtcOfferRejections;
    private long videoFrameRejections;
    private long sceneFailures;

    public synchronized void record(Event event) {
        switch (event) {
            case VIEWER_LIMIT_REJECTION -> viewerLimitRejections = increment(
                    viewerLimitRejections);
            case WEBRTC_OFFER_REJECTION -> webRtcOfferRejections = increment(
                    webRtcOfferRejections);
            case VIDEO_FRAME_REJECTION -> videoFrameRejections = increment(
                    videoFrameRejections);
            case SCENE_FAILURE -> sceneFailures = increment(sceneFailures);
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(viewerLimitRejections, webRtcOfferRejections,
                videoFrameRejections, sceneFailures);
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }
}
