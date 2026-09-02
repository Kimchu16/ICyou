package com.matissjurevics.icyou.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.device.TerminalRef;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction;

/** Tracks terminal snapshot subscriptions by stable terminal UUID. */
public final class DeviceSubscriptions {

    private static final Map<UUID, Set<UUID>> SUBSCRIPTIONS = new HashMap<>();

    private DeviceSubscriptions() {
    }

    public static void subscribe(UUID terminalId, UUID playerId) {
        SUBSCRIPTIONS.computeIfAbsent(terminalId, key -> new HashSet<>()).add(playerId);
    }

    public static void unsubscribe(UUID terminalId, UUID playerId) {
        Set<UUID> subscribers = SUBSCRIPTIONS.get(terminalId);
        if (subscribers != null) {
            subscribers.remove(playerId);
            if (subscribers.isEmpty()) {
                SUBSCRIPTIONS.remove(terminalId);
            }
        }
    }

    public static void unsubscribeAll(UUID playerId) {
        SUBSCRIPTIONS.values().forEach(subscribers -> subscribers.remove(playerId));
        SUBSCRIPTIONS.values().removeIf(Set::isEmpty);
    }

    public static void broadcast(MinecraftServer server, UUID terminalId) {
        broadcast(server, terminalId, false);
    }

    public static void broadcastAll(MinecraftServer server) {
        for (UUID terminalId : new HashSet<>(SUBSCRIPTIONS.keySet())) {
            broadcast(server, terminalId, false);
        }
    }

    public static void broadcast(MinecraftServer server, UUID terminalId, boolean openGui) {
        Set<UUID> subscribers = SUBSCRIPTIONS.get(terminalId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        TerminalRef terminal = GlobalDeviceRegistry.get(server).terminal(terminalId)
                .orElseThrow().ref();
        DeviceSnapshotS2CPayload payload = buildSnapshot(server, terminal, openGui);
        for (UUID playerId : new HashSet<>(subscribers)) {
            var player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static DeviceSnapshotS2CPayload buildSnapshot(MinecraftServer server,
                                                         TerminalRef terminal,
                                                         boolean openGui) {
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(server);
        var registeredTerminal = registry.terminal(terminal.deviceId())
                .filter(entry -> entry.ref().equals(terminal))
                .orElseThrow(() -> new IllegalArgumentException("Unknown terminal reference"));

        List<DeviceSnapshotS2CPayload.Cam> cameras = registry.camerasFor(
                terminal.deviceId()).stream().map(camera -> {
            ServerWorld world = server.getWorld(camera.ref().dimension());
            var state = world == null ? null : world.getBlockState(camera.ref().position());
            boolean online = state != null && state.getBlock() instanceof CameraBlock;
            int facingId = online ? state.get(CameraBlock.FACING).getId()
                    : Direction.NORTH.getId();
            return new DeviceSnapshotS2CPayload.Cam(
                    camera.ref(), camera.name(), facingId, online);
        }).toList();

        List<DeviceSnapshotS2CPayload.Scr> screens = registry.screensFor(
                terminal.deviceId()).stream().map(screen -> {
            GlobalDeviceRegistry.CameraEntry camera = screen.assignedCameraId()
                    .flatMap(registry::camera).orElse(null);
            ServerWorld cameraWorld = camera == null
                    ? null : server.getWorld(camera.ref().dimension());
            boolean online = cameraWorld != null && cameraWorld.getBlockState(
                    camera.ref().position()).getBlock() instanceof CameraBlock;
            return new DeviceSnapshotS2CPayload.Scr(screen.ref(), screen.name(),
                    screen.assignedCameraId(), camera == null ? "—" : camera.name(), online);
        }).toList();

        return new DeviceSnapshotS2CPayload(openGui, registeredTerminal.ref(),
                registry.slug(terminal.deviceId()), cameras, screens, List.of());
    }
}
