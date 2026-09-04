package com.matissjurevics.icyou.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraAdminLimitsTest {

    @TempDir Path directory;

    @Test
    void missingFileUsesDocumentedDefaults() throws Exception {
        assertEquals(CameraAdminLimits.DEFAULTS,
                CameraAdminLimits.load(directory.resolve("missing.properties")));
    }

    @Test
    void loadsEveryValidatedSetting() throws Exception {
        Path file = directory.resolve("limits.properties");
        Files.writeString(file, """
                limits.registered-cameras=100
                limits.active-cameras=8
                limits.viewers-per-camera=12
                limits.total-viewers=40
                limits.simulated-chunk-diameter=5
                limits.resource-grace-seconds=60
                """);

        assertEquals(new CameraAdminLimits(100, 8, 12, 40, 5, 60),
                CameraAdminLimits.load(file));
    }

    @Test
    void rejectsUnsafeOrContradictorySettings() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new CameraAdminLimits(4, 5, 1, 1, 3, 30));
        assertThrows(IllegalArgumentException.class,
                () -> new CameraAdminLimits(64, 4, 9, 8, 3, 30));
        assertThrows(IllegalArgumentException.class,
                () -> new CameraAdminLimits(64, 4, 8, 16, 4, 30));
        assertThrows(IllegalArgumentException.class,
                () -> new CameraAdminLimits(64, 64, 8, 16, 3, 30));
        Path file = directory.resolve("bad.properties");
        Files.writeString(file, "limits.active-cameras=not-a-number");
        assertThrows(IOException.class, () -> CameraAdminLimits.load(file));
    }
}
