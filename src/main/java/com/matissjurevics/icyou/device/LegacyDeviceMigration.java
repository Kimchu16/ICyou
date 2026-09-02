package com.matissjurevics.icyou.device;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.terminal.DeviceRegistry;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

/** One-time, backup-first conversion of position/int legacy registries. */
public final class LegacyDeviceMigration {

    private static final String LEGACY_FILE_NAME = "icyou_devices.dat";
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private LegacyDeviceMigration() {
    }

    public static void migrateIfNeeded(MinecraftServer server) {
        GlobalDeviceRegistry current = GlobalDeviceRegistry.get(server);
        if (current.isLegacyMigrationComplete()) {
            return;
        }

        Path worldRoot = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        List<LegacySource> sources = findLegacySources(server, worldRoot);
        if (sources.isEmpty()) {
            GlobalDeviceRegistry candidate = GlobalDeviceRegistry.copyOf(
                    current, server.getOverworld().getRegistryManager());
            candidate.markLegacyMigrationComplete();
            GlobalDeviceRegistry.install(server, candidate);
            server.getOverworld().getPersistentStateManager().save();
            ICyouMod.LOGGER.info("No ICyou 0.2.0 registry files found; migration marked complete");
            return;
        }

        Instant startedAt = Instant.now();
        Path backupRoot = worldRoot.resolve("icyou-migration-backups")
                .resolve(BACKUP_TIME.format(startedAt)).normalize();
        try {
            backupSources(worldRoot, backupRoot, sources);
            MigrationReport report = new MigrationReport(startedAt);
            sources.forEach(source -> report.note("Backed up " + source.file()));

            GlobalDeviceRegistry candidate = GlobalDeviceRegistry.copyOf(
                    current, server.getOverworld().getRegistryManager());
            for (LegacySource source : sources) {
                DeviceRegistry.LegacySnapshot snapshot = DeviceRegistry.get(source.world())
                        .migrationSnapshot();
                migrateDimension(candidate, source.world().getRegistryKey(), snapshot, report);
            }
            candidate.markLegacyMigrationComplete();

            Files.writeString(backupRoot.resolve("migration-report.txt"), report.render(),
                    StandardCharsets.UTF_8);
            GlobalDeviceRegistry.install(server, candidate);
            server.getOverworld().getPersistentStateManager().save();
            ICyouMod.LOGGER.info(
                    "ICyou legacy migration complete; backup/report: {} ({} ambiguities)",
                    backupRoot, report.ambiguityCount());
        } catch (Exception error) {
            ICyouMod.LOGGER.error(
                    "ICyou legacy migration aborted; original state was not replaced. Backup path: {}",
                    backupRoot, error);
        }
    }

    static void migrateDimension(GlobalDeviceRegistry target, RegistryKey<World> dimension,
                                 DeviceRegistry.LegacySnapshot snapshot,
                                 MigrationReport report) {
        Map<BlockPos, UUID> terminalIds = new HashMap<>();
        Set<BlockPos> terminalPositions = new LinkedHashSet<>(snapshot.slugs().keySet());
        snapshot.cameras().forEach(camera -> terminalPositions.add(camera.terminal()));
        snapshot.screens().forEach(screen -> terminalPositions.add(screen.terminal()));
        snapshot.wireless().forEach(wireless -> terminalPositions.add(wireless.terminal()));

        terminalPositions.stream().sorted(Comparator.comparingLong(BlockPos::asLong))
                .forEach(position -> migrateTerminal(
                        target, dimension, position, snapshot.slugs().get(position),
                        terminalIds, report));

        Map<Integer, UUID> cameraIds = new HashMap<>();
        snapshot.cameras().stream().sorted(Comparator.comparingInt(DeviceRegistry.CameraDevice::id))
                .forEach(camera -> migrateCamera(
                        target, dimension, camera, terminalIds, cameraIds, report));

        List<PendingAssignment> assignments = new ArrayList<>();
        snapshot.screens().stream().sorted(Comparator.comparingInt(DeviceRegistry.ScreenDevice::id))
                .forEach(screen -> migrateScreen(
                        target, dimension, screen, terminalIds, assignments, report));

        for (PendingAssignment assignment : assignments) {
            UUID cameraId = cameraIds.get(assignment.legacyCameraId());
            if (cameraId == null) {
                report.ambiguity("Screen " + assignment.screenId()
                        + " referenced missing legacy camera ID " + assignment.legacyCameraId());
                continue;
            }
            try {
                target.assignCamera(assignment.screenId(), java.util.Optional.of(cameraId));
            } catch (IllegalArgumentException error) {
                report.ambiguity("Could not restore screen assignment: " + error.getMessage());
            }
        }

        if (!snapshot.wireless().isEmpty()) {
            report.note(snapshot.wireless().size() + " portable-screen registry entries in "
                    + dimension.getValue() + " are retained in the backup; item links upgrade lazily");
        }
    }

    private static void migrateTerminal(GlobalDeviceRegistry target,
                                        RegistryKey<World> dimension, BlockPos position,
                                        String legacySlug, Map<BlockPos, UUID> terminalIds,
                                        MigrationReport report) {
        DeviceLocation location = new DeviceLocation(dimension, position);
        var existing = target.deviceAt(location);
        if (existing.isPresent()) {
            if (existing.get() instanceof TerminalRef terminal) {
                terminalIds.put(position, terminal.deviceId());
                report.note("Reused existing terminal " + terminal.deviceId() + " at " + location);
            } else {
                report.ambiguity("Terminal location occupied by another device: " + location);
            }
            return;
        }

        TerminalRef ref = new TerminalRef(stableId("terminal", dimension, 0, position),
                dimension, position);
        try {
            if (legacySlug == null || legacySlug.isBlank()) {
                target.registerTerminal(ref);
            } else {
                try {
                    target.registerMigratedTerminal(ref, legacySlug);
                } catch (IllegalArgumentException duplicateSlug) {
                    target.registerTerminal(ref);
                    report.ambiguity("Terminal slug '" + legacySlug
                            + "' was duplicated; generated a replacement");
                }
            }
            terminalIds.put(position, ref.deviceId());
            report.migratedTerminal();
        } catch (IllegalArgumentException error) {
            report.ambiguity("Could not migrate terminal at " + location + ": "
                    + error.getMessage());
        }
    }

    private static void migrateCamera(GlobalDeviceRegistry target,
                                      RegistryKey<World> dimension,
                                      DeviceRegistry.CameraDevice legacy,
                                      Map<BlockPos, UUID> terminalIds,
                                      Map<Integer, UUID> cameraIds,
                                      MigrationReport report) {
        UUID terminalId = terminalIds.get(legacy.terminal());
        if (terminalId == null) {
            report.ambiguity("Camera " + legacy.id() + " has no migratable terminal");
            return;
        }
        if (cameraIds.containsKey(legacy.id())) {
            report.ambiguity("Duplicate legacy camera ID " + legacy.id()
                    + " in " + dimension.getValue() + "; first entry kept");
            return;
        }

        DeviceLocation location = new DeviceLocation(dimension, legacy.pos());
        var existing = target.deviceAt(location);
        if (existing.isPresent()) {
            if (existing.get() instanceof CameraRef camera) {
                target.relinkCamera(camera.deviceId(), terminalId);
                cameraIds.put(legacy.id(), camera.deviceId());
                report.note("Reused existing camera " + camera.deviceId() + " at " + location);
            } else {
                report.ambiguity("Camera location occupied by another device: " + location);
            }
            return;
        }

        CameraRef ref = new CameraRef(stableId("camera", dimension, legacy.id(), legacy.pos()),
                dimension, legacy.pos());
        try {
            target.registerCamera(ref, terminalId, safeName(legacy.name(), "CAM", legacy.id()));
            cameraIds.put(legacy.id(), ref.deviceId());
            report.migratedCamera();
        } catch (IllegalArgumentException error) {
            report.ambiguity("Could not migrate camera " + legacy.id() + ": "
                    + error.getMessage());
        }
    }

    private static void migrateScreen(GlobalDeviceRegistry target,
                                      RegistryKey<World> dimension,
                                      DeviceRegistry.ScreenDevice legacy,
                                      Map<BlockPos, UUID> terminalIds,
                                      List<PendingAssignment> assignments,
                                      MigrationReport report) {
        UUID terminalId = terminalIds.get(legacy.terminal());
        if (terminalId == null) {
            report.ambiguity("Screen " + legacy.id() + " has no migratable terminal");
            return;
        }
        DeviceLocation location = new DeviceLocation(dimension, legacy.pos());
        ScreenRef ref;
        var existing = target.deviceAt(location);
        if (existing.isPresent()) {
            if (!(existing.get() instanceof ScreenRef screen)) {
                report.ambiguity("Screen location occupied by another device: " + location);
                return;
            }
            ref = screen;
            target.relinkScreen(ref.deviceId(), terminalId);
            report.note("Reused existing screen " + ref.deviceId() + " at " + location);
        } else {
            ref = new ScreenRef(stableId("screen", dimension, legacy.id(), legacy.pos()),
                    dimension, legacy.pos());
            try {
                target.registerScreen(ref, terminalId,
                        safeName(legacy.name(), "SCR", legacy.id()), java.util.Optional.empty());
                report.migratedScreen();
            } catch (IllegalArgumentException error) {
                report.ambiguity("Could not migrate screen " + legacy.id() + ": "
                        + error.getMessage());
                return;
            }
        }
        if (legacy.assignedCamId() >= 0) {
            assignments.add(new PendingAssignment(ref.deviceId(), legacy.assignedCamId()));
        }
    }

    private static List<LegacySource> findLegacySources(MinecraftServer server, Path worldRoot) {
        List<LegacySource> sources = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            Path dimensionRoot = DimensionType.getSaveDirectory(
                    world.getRegistryKey(), worldRoot).toAbsolutePath().normalize();
            Path file = dimensionRoot.resolve("data").resolve(LEGACY_FILE_NAME).normalize();
            if (Files.isRegularFile(file)) {
                sources.add(new LegacySource(world, file));
            }
        }
        return sources;
    }

    private static void backupSources(Path worldRoot, Path backupRoot,
                                      List<LegacySource> sources) throws IOException {
        Files.createDirectories(backupRoot);
        for (LegacySource source : sources) {
            backupFile(worldRoot, backupRoot, source.file());
        }
    }

    static Path backupFile(Path worldRoot, Path backupRoot, Path source) throws IOException {
        Path normalizedRoot = worldRoot.toAbsolutePath().normalize();
        Path normalizedBackup = backupRoot.toAbsolutePath().normalize();
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (!normalizedSource.startsWith(normalizedRoot)) {
            throw new IOException("Legacy file is outside the world directory: " + source);
        }
        Path destination = normalizedBackup.resolve(
                normalizedRoot.relativize(normalizedSource)).normalize();
        if (!destination.startsWith(normalizedBackup)) {
            throw new IOException("Legacy backup path escaped backup directory: " + source);
        }
        Files.createDirectories(destination.getParent());
        return Files.copy(normalizedSource, destination, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static UUID stableId(String type, RegistryKey<World> dimension,
                                 int legacyId, BlockPos position) {
        String source = "icyou:0.2.0:" + type + ':' + dimension.getValue() + ':'
                + legacyId + ':' + position.asLong();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeName(String name, String prefix, int legacyId) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() || trimmed.length() > 24
                ? prefix + '-' + legacyId : trimmed;
    }

    private record LegacySource(ServerWorld world, Path file) {
    }

    private record PendingAssignment(UUID screenId, int legacyCameraId) {
    }
}
