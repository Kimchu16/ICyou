package com.matissjurevics.icyou.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class GlobalDeviceRegistryTest {

    private static final RegistryKey<World> OVERWORLD = world("overworld_test");

    @Test
    void configurableRegistrationLimitBlocksNewAndRestoredCameras() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef terminal = new TerminalRef(UUID.randomUUID(), OVERWORLD,
                new BlockPos(0, 64, 0));
        registry.registerTerminal(terminal);
        registry.setRegisteredCameraLimit(1);
        CameraRef first = new CameraRef(UUID.randomUUID(), OVERWORLD,
                new BlockPos(1, 64, 0));
        registry.registerCamera(first, terminal.deviceId(), "First");

        assertFalse(registry.hasRegisteredCameraCapacity());
        assertThrows(IllegalStateException.class, () -> registry.registerCamera(
                new CameraRef(UUID.randomUUID(), OVERWORLD, new BlockPos(2, 64, 0)),
                terminal.deviceId(), "Second"));
        CameraRef migrated = new CameraRef(UUID.randomUUID(), OVERWORLD,
                new BlockPos(4, 64, 0));
        registry.registerMigratedCamera(migrated, terminal.deviceId(), "Migrated");
        assertTrue(registry.camera(migrated.deviceId()).isPresent());
        registry.tombstoneCamera(migrated.deviceId(), Instant.EPOCH);
        registry.tombstoneCamera(first.deviceId(), Instant.EPOCH);
        CameraRef second = new CameraRef(UUID.randomUUID(), OVERWORLD,
                new BlockPos(2, 64, 0));
        registry.registerCamera(second, terminal.deviceId(), "Second");
        CameraRef replacement = new CameraRef(first.deviceId(), OVERWORLD,
                new BlockPos(3, 64, 0));
        assertThrows(IllegalStateException.class,
                () -> registry.restoreCamera(first.deviceId(), replacement));
        registry.tombstoneCamera(second.deviceId(), Instant.EPOCH);
        registry.restoreCamera(first.deviceId(), replacement);
        assertFalse(registry.hasRegisteredCameraCapacity());
    }
    private static final RegistryKey<World> NETHER = world("nether_test");

    @Test
    void indexesDevicesByUuidLocationAndTerminalAcrossDimensions() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef terminal = terminal(1, OVERWORLD, new BlockPos(0, 64, 0));
        CameraRef camera = camera(2, OVERWORLD, new BlockPos(4, 64, 4));
        ScreenRef screen = screen(3, NETHER, new BlockPos(4, 64, 4));

        registry.registerTerminal(terminal);
        registry.registerCamera(camera, terminal.deviceId(), "Front Gate");
        registry.registerScreen(screen, terminal.deviceId(), "Control Room",
                Optional.of(camera.deviceId()));

        assertEquals(3, registry.deviceCount());
        assertEquals(camera, registry.device(camera.deviceId()).orElseThrow());
        assertEquals(camera, registry.deviceAt(DeviceLocation.of(camera)).orElseThrow());
        assertEquals(screen, registry.deviceAt(DeviceLocation.of(screen)).orElseThrow());
        assertEquals(1, registry.camerasFor(terminal.deviceId()).size());
        assertEquals(1, registry.screensFor(terminal.deviceId()).size());
        assertTrue(registry.isDirty());
    }

    @Test
    void rejectsDuplicateIdentityLocationAndInvalidRelationships() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef firstTerminal = terminal(10, OVERWORLD, new BlockPos(0, 64, 0));
        TerminalRef secondTerminal = terminal(11, OVERWORLD, new BlockPos(10, 64, 0));
        CameraRef camera = camera(12, OVERWORLD, new BlockPos(1, 64, 0));
        registry.registerTerminal(firstTerminal);
        registry.registerTerminal(secondTerminal);
        registry.registerCamera(camera, firstTerminal.deviceId(), "Camera");

        assertThrows(IllegalArgumentException.class, () -> registry.registerScreen(
                new ScreenRef(camera.deviceId(), NETHER, new BlockPos(2, 64, 0)),
                firstTerminal.deviceId(), "Duplicate UUID", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> registry.registerScreen(
                screen(13, OVERWORLD, camera.position()), firstTerminal.deviceId(),
                "Duplicate location", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> registry.registerCamera(
                camera(14, OVERWORLD, new BlockPos(2, 64, 0)), uuid(99),
                "Missing terminal"));
        assertThrows(IllegalArgumentException.class, () -> registry.registerScreen(
                screen(15, OVERWORLD, new BlockPos(3, 64, 0)), secondTerminal.deviceId(),
                "Wrong terminal", Optional.of(camera.deviceId())));
    }

    @Test
    void persistenceRoundTripRebuildsAllIndexesAndAssignments() {
        GlobalDeviceRegistry original = new GlobalDeviceRegistry();
        TerminalRef terminal = terminal(20, OVERWORLD, new BlockPos(-1, 70, -1));
        CameraRef camera = camera(21, NETHER, new BlockPos(20, 50, 20));
        ScreenRef screen = screen(22, OVERWORLD, new BlockPos(1, 70, 1));
        original.registerTerminal(terminal);
        original.registerCamera(camera, terminal.deviceId(), "Nether Yard");
        original.registerScreen(screen, terminal.deviceId(), "Main Screen",
                Optional.of(camera.deviceId()));
        original.markLegacyMigrationComplete();

        NbtCompound saved = original.writeNbt(new NbtCompound(), null);
        saved.remove("cameraTombstones");
        GlobalDeviceRegistry restored = GlobalDeviceRegistry.readNbt(saved, null);

        assertEquals(original.deviceCount(), restored.deviceCount());
        assertEquals(original.terminal(terminal.deviceId()), restored.terminal(terminal.deviceId()));
        assertEquals(original.camera(camera.deviceId()), restored.camera(camera.deviceId()));
        assertEquals(original.screen(screen.deviceId()), restored.screen(screen.deviceId()));
        assertEquals(camera, restored.deviceAt(DeviceLocation.of(camera)).orElseThrow());
        assertEquals(original.slug(terminal.deviceId()), restored.slug(terminal.deviceId()));
        assertEquals(terminal, restored.terminalBySlug(restored.slug(terminal.deviceId()))
                .orElseThrow().ref());
        assertFalse(restored.isDirty());
        assertTrue(restored.terminal(terminal.deviceId()).orElseThrow().ownerId().isEmpty());
        assertTrue(restored.isLegacyMigrationComplete());

        saved.putInt("schemaVersion", CameraOverhaulContracts.SAVE_SCHEMA_VERSION + 1);
        assertThrows(IllegalArgumentException.class,
                () -> GlobalDeviceRegistry.readNbt(saved, null));
    }

    @Test
    void removalsKeepRelationshipIndexesConsistent() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef terminal = terminal(30, OVERWORLD, new BlockPos(0, 80, 0));
        CameraRef camera = camera(31, OVERWORLD, new BlockPos(1, 80, 0));
        ScreenRef screen = screen(32, OVERWORLD, new BlockPos(2, 80, 0));
        registry.registerTerminal(terminal);
        registry.registerCamera(camera, terminal.deviceId(), "Camera");
        registry.registerScreen(screen, terminal.deviceId(), "Screen",
                Optional.of(camera.deviceId()));

        assertTrue(registry.removeCamera(camera.deviceId()));
        assertTrue(registry.screen(screen.deviceId()).orElseThrow().assignedCameraId().isEmpty());
        assertTrue(registry.camerasFor(terminal.deviceId()).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> registry.removeTerminal(terminal.deviceId()));
        assertTrue(registry.removeScreen(screen.deviceId()));
        assertTrue(registry.removeTerminal(terminal.deviceId()));
        assertEquals(0, registry.deviceCount());
    }

    @Test
    void relinkingMovesTerminalIndexesAndClearsInvalidAssignments() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef first = terminal(40, OVERWORLD, new BlockPos(0, 90, 0));
        TerminalRef second = terminal(41, NETHER, new BlockPos(0, 90, 0));
        CameraRef camera = camera(42, OVERWORLD, new BlockPos(1, 90, 0));
        ScreenRef screen = screen(43, OVERWORLD, new BlockPos(2, 90, 0));
        registry.registerTerminal(first);
        registry.registerTerminal(second);
        registry.registerCamera(camera, first.deviceId(), "Camera");
        registry.registerScreen(screen, first.deviceId(), "Screen",
                Optional.of(camera.deviceId()));

        registry.relinkCamera(camera.deviceId(), second.deviceId());

        assertTrue(registry.camerasFor(first.deviceId()).isEmpty());
        assertEquals(camera, registry.camerasFor(second.deviceId()).getFirst().ref());
        assertTrue(registry.screen(screen.deviceId()).orElseThrow()
                .assignedCameraId().isEmpty());

        registry.relinkScreen(screen.deviceId(), second.deviceId());
        assertTrue(registry.screensFor(first.deviceId()).isEmpty());
        assertEquals(screen, registry.screensFor(second.deviceId()).getFirst().ref());
    }

    @Test
    void tracksClaimsTransfersAndOperatorManagement() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef placed = terminal(50, OVERWORLD, new BlockPos(0, 64, 0));
        TerminalRef migrated = terminal(51, OVERWORLD, new BlockPos(1, 64, 0));
        UUID owner = uuid(500);
        UUID nextOwner = uuid(501);
        registry.registerTerminal(placed, owner);
        registry.registerTerminal(migrated);

        assertTrue(registry.canManageTerminal(placed.deviceId(), owner, false));
        assertFalse(registry.canManageTerminal(placed.deviceId(), nextOwner, false));
        assertTrue(registry.canManageTerminal(placed.deviceId(), nextOwner, true));
        assertTrue(registry.claimTerminal(migrated.deviceId(), nextOwner));
        assertFalse(registry.claimTerminal(migrated.deviceId(), owner));

        registry.transferTerminal(placed.deviceId(), nextOwner);
        assertFalse(registry.canManageTerminal(placed.deviceId(), owner, false));
        assertTrue(registry.canManageTerminal(placed.deviceId(), nextOwner, false));
    }

    @Test
    void tombstonesPreserveIdentityOwnershipAndAssignmentsUntilRestore() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        UUID owner = uuid(600);
        TerminalRef terminal = terminal(60, OVERWORLD, new BlockPos(0, 70, 0));
        CameraRef camera = camera(61, OVERWORLD, new BlockPos(1, 70, 0));
        ScreenRef screen = screen(62, OVERWORLD, new BlockPos(2, 70, 0));
        registry.registerTerminal(terminal, owner);
        registry.registerCamera(camera, terminal.deviceId(), "North gate");
        registry.registerScreen(screen, terminal.deviceId(), "Guard desk",
                Optional.of(camera.deviceId()));
        Instant brokenAt = Instant.parse("2026-09-01T12:00:00Z");

        assertTrue(registry.tombstoneCamera(camera.deviceId(), brokenAt));
        assertTrue(registry.camera(camera.deviceId()).isEmpty());
        assertTrue(registry.deviceAt(DeviceLocation.of(camera)).isEmpty());
        assertEquals(owner, registry.terminal(terminal.deviceId()).orElseThrow()
                .ownerId().orElseThrow());
        assertEquals(camera.deviceId(), registry.screen(screen.deviceId()).orElseThrow()
                .assignedCameraId().orElseThrow());

        CameraRef replacement = camera(61, NETHER, new BlockPos(9, 80, 9));
        GlobalDeviceRegistry.CameraEntry restored = registry.restoreCamera(
                camera.deviceId(), replacement);
        assertEquals(replacement, restored.ref());
        assertEquals("North gate", restored.name());
        assertEquals(terminal.deviceId(), restored.terminalId());
        assertTrue(registry.cameraTombstone(camera.deviceId()).isEmpty());
        assertEquals(replacement, registry.deviceAt(DeviceLocation.of(replacement)).orElseThrow());
        assertEquals(camera.deviceId(), registry.screen(screen.deviceId()).orElseThrow()
                .assignedCameraId().orElseThrow());
    }

    @Test
    void tombstoneRestoreIsAtomicAndExpiryClearsAssignments() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef terminal = terminal(70, OVERWORLD, new BlockPos(0, 75, 0));
        CameraRef camera = camera(71, OVERWORLD, new BlockPos(1, 75, 0));
        ScreenRef screen = screen(72, OVERWORLD, new BlockPos(2, 75, 0));
        CameraRef occupied = camera(73, NETHER, new BlockPos(3, 75, 0));
        registry.registerTerminal(terminal, uuid(700));
        registry.registerCamera(camera, terminal.deviceId(), "Camera");
        registry.registerCamera(occupied, terminal.deviceId(), "Occupied");
        registry.registerScreen(screen, terminal.deviceId(), "Screen",
                Optional.of(camera.deviceId()));
        Instant brokenAt = Instant.parse("2026-08-01T00:00:00Z");
        registry.tombstoneCamera(camera.deviceId(), brokenAt);

        CameraRef invalidReplacement = camera(71, NETHER, occupied.position());
        assertThrows(IllegalArgumentException.class,
                () -> registry.restoreCamera(camera.deviceId(), invalidReplacement));
        assertTrue(registry.cameraTombstone(camera.deviceId()).isPresent());
        assertEquals(0, registry.purgeExpiredTombstones(
                brokenAt.plusSeconds(30L * 24 * 60 * 60 - 1)));
        assertEquals(1, registry.purgeExpiredTombstones(
                brokenAt.plusSeconds(30L * 24 * 60 * 60)));
        assertTrue(registry.cameraTombstone(camera.deviceId()).isEmpty());
        assertTrue(registry.screen(screen.deviceId()).orElseThrow().assignedCameraId().isEmpty());
    }

    @Test
    void persistenceKeepsOwnersAndTombstones() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        UUID owner = uuid(800);
        TerminalRef terminal = terminal(80, OVERWORLD, new BlockPos(0, 90, 0));
        CameraRef camera = camera(81, NETHER, new BlockPos(1, 90, 0));
        registry.registerTerminal(terminal, owner);
        registry.registerCamera(camera, terminal.deviceId(), "Archive");
        Instant brokenAt = Instant.parse("2026-09-02T10:15:30Z");
        registry.tombstoneCamera(camera.deviceId(), brokenAt);

        GlobalDeviceRegistry restored = GlobalDeviceRegistry.readNbt(
                registry.writeNbt(new NbtCompound(), null), null);

        assertEquals(owner, restored.terminal(terminal.deviceId()).orElseThrow()
                .ownerId().orElseThrow());
        GlobalDeviceRegistry.CameraTombstone tombstone = restored.cameraTombstone(
                camera.deviceId()).orElseThrow();
        assertEquals(camera, tombstone.lastRef());
        assertEquals(terminal.deviceId(), tombstone.terminalId());
        assertEquals("Archive", tombstone.name());
        assertEquals(brokenAt, tombstone.brokenAt());
        assertFalse(restored.isDirty());
    }

    private static RegistryKey<World> world(String path) {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of("icyou", path));
    }

    private static TerminalRef terminal(int id, RegistryKey<World> world, BlockPos position) {
        return new TerminalRef(uuid(id), world, position);
    }

    private static CameraRef camera(int id, RegistryKey<World> world, BlockPos position) {
        return new CameraRef(uuid(id), world, position);
    }

    private static ScreenRef screen(int id, RegistryKey<World> world, BlockPos position) {
        return new ScreenRef(uuid(id), world, position);
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }
}
