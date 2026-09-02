package com.matissjurevics.icyou.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

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

        NbtCompound saved = original.writeNbt(new NbtCompound(), null);
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
