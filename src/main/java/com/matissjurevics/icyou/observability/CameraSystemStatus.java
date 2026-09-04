package com.matissjurevics.icyou.observability;

import java.util.List;

/** Immutable, privacy-safe operator snapshot of the camera runtime. */
public record CameraSystemStatus(
        int cameras, int registeredCameraLimit,
        int activatingFeeds, int availableFeeds, int unavailableFeeds,
        int retainingFeeds, int viewers, int totalViewerLimit,
        int renderAgents, int jobs, int readyJobs, int leasedChunks,
        int cachedFrames, int webRtcPeers, boolean webListenerActive,
        CameraEventCounters.Snapshot events) {

    public enum State {
        IDLE,
        RUNNING,
        DEGRADED
    }

    public CameraSystemStatus {
        nonnegative(cameras, registeredCameraLimit, activatingFeeds, availableFeeds,
                unavailableFeeds, retainingFeeds, viewers, totalViewerLimit,
                renderAgents, jobs, readyJobs, leasedChunks, cachedFrames, webRtcPeers);
        if (registeredCameraLimit < 1 || totalViewerLimit < 1) {
            throw new IllegalArgumentException("Camera status limits must be positive");
        }
        // Existing saved cameras may intentionally remain above a newly lowered
        // creation limit; the status surface must still be able to report them.
        if (viewers > totalViewerLimit || readyJobs > jobs) {
            throw new IllegalArgumentException("Camera status counts exceed their limits");
        }
        if (events == null) throw new NullPointerException("events");
    }

    public State state() {
        if (unavailableFeeds > 0) return State.DEGRADED;
        return activatingFeeds + availableFeeds + retainingFeeds + viewers + jobs > 0
                ? State.RUNNING : State.IDLE;
    }

    public List<String> lines() {
        return List.of(
                "ICyou status: " + state().name().toLowerCase() + ".",
                "Cameras: " + cameras + "/" + registeredCameraLimit
                        + " registered. Feeds: " + activatingFeeds + " starting, "
                        + availableFeeds + " ready, " + unavailableFeeds
                        + " unavailable, " + retainingFeeds + " retaining.",
                "Live: " + viewers + "/" + totalViewerLimit + " viewers, "
                        + renderAgents + " render agents, " + jobs + " jobs ("
                        + readyJobs + " ready), " + webRtcPeers + " WebRTC peers.",
                "Resources: " + leasedChunks + " leased chunks, " + cachedFrames
                        + " cached frames, web listener "
                        + (webListenerActive ? "on" : "off") + ".",
                "Since startup: " + events.viewerLimitRejections()
                        + " viewer-limit rejects, " + events.webRtcOfferRejections()
                        + " WebRTC offer rejects, " + events.videoFrameRejections()
                        + " invalid frames, " + events.sceneFailures()
                        + " scene failures.");
    }

    private static void nonnegative(int... values) {
        for (int value : values) {
            if (value < 0) throw new IllegalArgumentException(
                    "Camera status counts cannot be negative");
        }
    }
}
