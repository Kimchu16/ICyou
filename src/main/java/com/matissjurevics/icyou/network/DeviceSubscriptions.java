package com.matissjurevics.icyou.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.terminal.DeviceRegistry;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Tracks which players have a terminal GUI or portable-screen HUD open, and
 * broadcasts fresh device snapshots to them after any change.
 */
public final class DeviceSubscriptions {

    private DeviceSubscriptions() {}

    private static final Map<BlockPos, Set<UUID>> SUBS = new HashMap<>();

    public static void subscribe(BlockPos terminal, UUID uuid) {
        SUBS.computeIfAbsent(terminal.toImmutable(), k -> new HashSet<>()).add(uuid);
    }

    public static void unsubscribe(BlockPos terminal, UUID uuid) {
        Set<UUID> set = SUBS.get(terminal);
        if (set != null) {
            set.remove(uuid);
            if (set.isEmpty()) {
                SUBS.remove(terminal);
            }
        }
    }

    public static void unsubscribeAll(UUID uuid) {
        SUBS.values().forEach(set -> set.remove(uuid));
        SUBS.values().removeIf(Set::isEmpty);
    }

    /** Sends a fresh snapshot for a terminal to all its subscribers. */
    public static void broadcast(ServerWorld world, BlockPos terminal) {
        broadcast(world, terminal, false);
    }

    /** Refreshes every subscribed terminal (used on block breaks). */
    public static void broadcastAll(ServerWorld world) {
        for (BlockPos terminal : new HashSet<>(SUBS.keySet())) {
            broadcast(world, terminal, false);
        }
    }

    /** Sends a fresh snapshot; optionally tells clients to open the GUI. */
    public static void broadcast(ServerWorld world, BlockPos terminal, boolean openGui) {
        Set<UUID> set = SUBS.get(terminal);
        if (set == null || set.isEmpty()) {
            return;
        }
        DeviceSnapshotS2CPayload payload = buildSnapshot(world, terminal, openGui);
        for (UUID uuid : new HashSet<>(set)) {
            var player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /** Builds the enriched snapshot for a terminal. */
    public static DeviceSnapshotS2CPayload buildSnapshot(ServerWorld world, BlockPos terminal,
                                                         boolean openGui) {
        DeviceRegistry reg = DeviceRegistry.get(world);

        List<DeviceSnapshotS2CPayload.Cam> cameras = reg.camerasFor(terminal).stream().map(c -> {
            var state = world.getBlockState(c.pos());
            int facingId = state.getBlock() instanceof CameraBlock
                    ? state.get(CameraBlock.FACING).getId()
                    : Direction.NORTH.getId();
            boolean online = state.getBlock() instanceof CameraBlock;
            return new DeviceSnapshotS2CPayload.Cam(
                    c.id(), c.name(), c.pos(), facingId, online);
        }).toList();

        List<DeviceSnapshotS2CPayload.Scr> screens = reg.screensFor(terminal).stream().map(s -> {
            DeviceRegistry.CameraDevice camDevice = s.assignedCamId() >= 0
                    ? reg.cameraById(s.assignedCamId()).orElse(null) : null;
            boolean online = camDevice != null
                    && world.getBlockState(camDevice.pos()).getBlock() instanceof CameraBlock;
            return new DeviceSnapshotS2CPayload.Scr(s.id(), s.name(), s.assignedCamId(),
                    camDevice != null ? camDevice.name() : "\u2014", online);
        }).toList();

        List<DeviceSnapshotS2CPayload.Wrl> wireless = reg.wirelessFor(terminal).stream()
                .map(w -> new DeviceSnapshotS2CPayload.Wrl(w.id(), w.name())).toList();

        return new DeviceSnapshotS2CPayload(openGui, terminal, cameras, screens, wireless);
    }
}
