package com.matissjurevics.icyou.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

/** Validated server-owned resource limits for the camera system. */
public record CameraAdminLimits(int registeredCameras, int activeCameras,
                                int viewersPerCamera, int totalViewers,
                                int simulatedChunkDiameter,
                                int resourceGraceSeconds) {

    public static final String FILE_NAME = "icyou-camera-limits.properties";
    public static final int HARD_MAX_REGISTERED_CAMERAS = 4_096;
    public static final int HARD_MAX_ACTIVE_CAMERAS = 64;
    public static final int HARD_MAX_VIEWERS_PER_CAMERA = 16;
    public static final int HARD_MAX_TOTAL_VIEWERS = 256;
    public static final int HARD_MAX_CHUNK_DIAMETER = 7;
    public static final int HARD_MAX_GRACE_SECONDS = 300;
    public static final int HARD_MAX_SIMULATED_CHUNKS = 256;

    public static final CameraAdminLimits DEFAULTS = new CameraAdminLimits(
            CameraOverhaulContracts.MAX_REGISTERED_CAMERAS,
            CameraOverhaulContracts.MAX_ACTIVE_CAMERAS,
            CameraOverhaulContracts.MAX_VIEWERS_PER_CAMERA,
            CameraOverhaulContracts.MAX_TOTAL_VIEWERS,
            CameraOverhaulContracts.SIMULATED_CHUNK_DIAMETER,
            CameraOverhaulContracts.RESOURCE_GRACE_SECONDS);

    public CameraAdminLimits {
        range("registered cameras", registeredCameras, 1, HARD_MAX_REGISTERED_CAMERAS);
        range("active cameras", activeCameras, 1, HARD_MAX_ACTIVE_CAMERAS);
        range("viewers per camera", viewersPerCamera, 1,
                HARD_MAX_VIEWERS_PER_CAMERA);
        range("total viewers", totalViewers, 1, HARD_MAX_TOTAL_VIEWERS);
        range("simulated chunk diameter", simulatedChunkDiameter, 1,
                HARD_MAX_CHUNK_DIAMETER);
        range("resource grace seconds", resourceGraceSeconds, 1,
                HARD_MAX_GRACE_SECONDS);
        if (activeCameras > registeredCameras) {
            throw new IllegalArgumentException(
                    "Active camera limit exceeds registered camera limit");
        }
        if (viewersPerCamera > totalViewers) {
            throw new IllegalArgumentException(
                    "Per-camera viewer limit exceeds total viewer limit");
        }
        if (simulatedChunkDiameter % 2 == 0) {
            throw new IllegalArgumentException(
                    "Simulated chunk diameter must be odd");
        }
        if ((long) activeCameras * simulatedChunkDiameter * simulatedChunkDiameter
                > HARD_MAX_SIMULATED_CHUNKS) {
            throw new IllegalArgumentException(
                    "Active camera and chunk-area limits exceed the safe chunk budget");
        }
    }

    public Duration resourceGracePeriod() {
        return Duration.ofSeconds(resourceGraceSeconds);
    }

    public static CameraAdminLimits load(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file)) return DEFAULTS;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        try {
            return new CameraAdminLimits(
                    integer(properties, "limits.registered-cameras",
                            DEFAULTS.registeredCameras),
                    integer(properties, "limits.active-cameras",
                            DEFAULTS.activeCameras),
                    integer(properties, "limits.viewers-per-camera",
                            DEFAULTS.viewersPerCamera),
                    integer(properties, "limits.total-viewers",
                            DEFAULTS.totalViewers),
                    integer(properties, "limits.simulated-chunk-diameter",
                            DEFAULTS.simulatedChunkDiameter),
                    integer(properties, "limits.resource-grace-seconds",
                            DEFAULTS.resourceGraceSeconds));
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid ICyou camera limits", error);
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key,
                Integer.toString(fallback)).trim());
    }

    private static void range(String label, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between "
                    + minimum + " and " + maximum);
        }
    }
}
