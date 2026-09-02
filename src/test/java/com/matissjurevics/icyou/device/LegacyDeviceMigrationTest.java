package com.matissjurevics.icyou.device;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.matissjurevics.icyou.terminal.DeviceRegistry;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class LegacyDeviceMigrationTest {

    private static final RegistryKey<World> DIMENSION = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of("icyou", "migration_test"));

    @Test
    void migratesLegacyRelationshipsWithStableDimensionAwareIds() {
        BlockPos terminalPos = new BlockPos(1, 64, 2);
        BlockPos cameraPos = new BlockPos(3, 65, 4);
        BlockPos screenPos = new BlockPos(5, 66, 6);
        DeviceRegistry.LegacySnapshot snapshot = new DeviceRegistry.LegacySnapshot(
                List.of(new DeviceRegistry.CameraDevice(7, "Front door", terminalPos, cameraPos)),
                List.of(new DeviceRegistry.ScreenDevice(
                        8, "Lobby", terminalPos, screenPos, 7)),
                List.of(), Map.of(terminalPos, "calm-otter"));

        GlobalDeviceRegistry first = new GlobalDeviceRegistry();
        LegacyDeviceMigration.migrateDimension(
                first, DIMENSION, snapshot, new MigrationReport(Instant.EPOCH));
        GlobalDeviceRegistry second = new GlobalDeviceRegistry();
        LegacyDeviceMigration.migrateDimension(
                second, DIMENSION, snapshot, new MigrationReport(Instant.EPOCH));

        TerminalRef terminal = (TerminalRef) first.deviceAt(
                new DeviceLocation(DIMENSION, terminalPos)).orElseThrow();
        CameraRef camera = (CameraRef) first.deviceAt(
                new DeviceLocation(DIMENSION, cameraPos)).orElseThrow();
        ScreenRef screen = (ScreenRef) first.deviceAt(
                new DeviceLocation(DIMENSION, screenPos)).orElseThrow();
        assertEquals("calm-otter", first.slug(terminal.deviceId()));
        assertEquals(terminal.deviceId(), first.camera(camera.deviceId()).orElseThrow().terminalId());
        assertEquals(terminal.deviceId(), first.screen(screen.deviceId()).orElseThrow().terminalId());
        assertEquals(camera.deviceId(), first.screen(screen.deviceId()).orElseThrow()
                .assignedCameraId().orElseThrow());
        assertEquals(terminal.deviceId(), second.deviceAt(
                new DeviceLocation(DIMENSION, terminalPos)).orElseThrow().deviceId());
        assertEquals(camera.deviceId(), second.deviceAt(
                new DeviceLocation(DIMENSION, cameraPos)).orElseThrow().deviceId());
        assertEquals(screen.deviceId(), second.deviceAt(
                new DeviceLocation(DIMENSION, screenPos)).orElseThrow().deviceId());
    }

    @Test
    void recordsAmbiguousLegacyRelationshipsWithoutDiscardingValidEntries() {
        BlockPos terminal = new BlockPos(0, 70, 0);
        DeviceRegistry.LegacySnapshot snapshot = new DeviceRegistry.LegacySnapshot(
                List.of(
                        new DeviceRegistry.CameraDevice(2, "First", terminal,
                                new BlockPos(1, 70, 0)),
                        new DeviceRegistry.CameraDevice(2, "Duplicate", terminal,
                                new BlockPos(2, 70, 0))),
                List.of(new DeviceRegistry.ScreenDevice(3, "Screen", terminal,
                        new BlockPos(3, 70, 0), 99)),
                List.of(), Map.of(terminal, "clear-fox"));
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        MigrationReport report = new MigrationReport(Instant.EPOCH);

        LegacyDeviceMigration.migrateDimension(registry, DIMENSION, snapshot, report);

        assertEquals(3, registry.deviceCount());
        assertEquals(2, report.ambiguityCount());
        assertTrue(report.render().contains("Duplicate legacy camera ID 2"));
        assertTrue(report.render().contains("missing legacy camera ID 99"));
    }

    @Test
    void copiesLegacyBytesInsideAWorldBeforeConversion(@TempDir Path temp) throws IOException {
        Path worldRoot = temp.resolve("world");
        Path source = worldRoot.resolve("dimensions/icyou/test/data/icyou_devices.dat");
        Files.createDirectories(source.getParent());
        byte[] original = new byte[] { 1, 3, 3, 7 };
        Files.write(source, original);
        Path backupRoot = worldRoot.resolve("icyou-migration-backups/run");

        Path copied = LegacyDeviceMigration.backupFile(worldRoot, backupRoot, source);

        assertTrue(copied.startsWith(backupRoot));
        assertArrayEquals(original, Files.readAllBytes(copied));
        assertThrows(IOException.class, () -> LegacyDeviceMigration.backupFile(
                worldRoot, backupRoot, temp.resolve("outside.dat")));
    }
}
