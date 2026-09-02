package com.matissjurevics.icyou.overhaul;

/**
 * Version and default-limit contract for the camera overhaul.
 *
 * <p>Changing a version constant requires a compatible reader or an explicit
 * rejection path. Runtime configuration may lower or raise the defaults, but
 * it must preserve the relationships enforced by {@link #validateLimits()}.</p>
 */
public final class CameraOverhaulContracts {

    public static final int SAVE_SCHEMA_VERSION = 1;
    public static final int DEVICE_NETWORK_PROTOCOL_VERSION = 1;

    public static final int MAX_REGISTERED_CAMERAS = 64;
    public static final int MAX_ACTIVE_CAMERAS = 4;
    public static final int MAX_VIEWERS_PER_CAMERA = 8;
    public static final int MAX_TOTAL_VIEWERS = 16;

    public static final int SIMULATED_CHUNK_DIAMETER = 3;
    public static final int RESOURCE_GRACE_SECONDS = 30;
    public static final int SCREEN_DEMAND_RANGE_BLOCKS = 64;
    public static final int TOMBSTONE_RETENTION_DAYS = 30;

    public static final int VIDEO_WIDTH = 854;
    public static final int VIDEO_HEIGHT = 480;
    public static final int VIDEO_FPS = 10;
    public static final int JPEG_QUALITY = 82;

    private CameraOverhaulContracts() {
    }

    public static void validateLimits() {
        if (SAVE_SCHEMA_VERSION < 1 || DEVICE_NETWORK_PROTOCOL_VERSION < 1) {
            throw new IllegalStateException("Overhaul protocol versions must be positive");
        }
        if (MAX_ACTIVE_CAMERAS > MAX_REGISTERED_CAMERAS) {
            throw new IllegalStateException("Active camera ceiling exceeds registered ceiling");
        }
        if (MAX_VIEWERS_PER_CAMERA > MAX_TOTAL_VIEWERS) {
            throw new IllegalStateException("Per-camera viewer ceiling exceeds total ceiling");
        }
        if (SIMULATED_CHUNK_DIAMETER < 1 || SIMULATED_CHUNK_DIAMETER % 2 == 0) {
            throw new IllegalStateException("Simulated chunk diameter must be positive and odd");
        }
        if (JPEG_QUALITY < 1 || JPEG_QUALITY > 100) {
            throw new IllegalStateException("JPEG quality must be between 1 and 100");
        }
    }
}
