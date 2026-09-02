package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.network.DeviceActionC2SPayload;
import com.matissjurevics.icyou.network.DeviceSnapshotS2CPayload;
import com.matissjurevics.icyou.network.DeviceSubscriptions;
import com.matissjurevics.icyou.network.DeviceSubscribeC2SPayload;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;
import com.matissjurevics.icyou.network.FeedDataS2CPayload;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;

/** All custom network payloads, their codecs and server-side handlers. */
public final class ModNetworking {

    private ModNetworking() {}

    public static void register() {
        // --- S2C codecs ---
        PayloadTypeRegistry.playS2C().register(FeedDataS2CPayload.ID, FeedDataS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnterCameraViewS2CPayload.ID,
                EnterCameraViewS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DeviceSnapshotS2CPayload.ID,
                DeviceSnapshotS2CPayload.CODEC);

        // --- C2S codecs ---
        PayloadTypeRegistry.playC2S().register(DeviceActionC2SPayload.ID,
                DeviceActionC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeviceSubscribeC2SPayload.ID,
                DeviceSubscribeC2SPayload.CODEC);

        // --- device mutations from the terminal GUI ---
        ServerPlayNetworking.registerGlobalReceiver(DeviceActionC2SPayload.ID, (payload, ctx) -> {
            var server = ctx.player().getServer();
            GlobalDeviceRegistry reg = GlobalDeviceRegistry.get(server);
            boolean ok = false;
            try {
                var terminal = reg.terminal(payload.terminal().deviceId())
                        .filter(entry -> entry.ref().equals(payload.terminal())).orElseThrow();
                ok = switch (payload.action()) {
                    case DeviceActionC2SPayload.ACTION_ASSIGN -> {
                        var screen = reg.screen(payload.id()).orElseThrow();
                        if (!screen.terminalId().equals(terminal.ref().deviceId())) {
                            yield false;
                        }
                        reg.assignCamera(payload.id(), payload.auxId());
                        yield true;
                    }
                    case DeviceActionC2SPayload.ACTION_RENAME -> {
                        if (payload.targetType() == DeviceActionC2SPayload.TYPE_CAMERA
                                && reg.camera(payload.id()).filter(entry -> entry.terminalId()
                                .equals(terminal.ref().deviceId())).isPresent()) {
                            reg.renameCamera(payload.id(), payload.name());
                            yield true;
                        }
                        if (payload.targetType() == DeviceActionC2SPayload.TYPE_SCREEN
                                && reg.screen(payload.id()).filter(entry -> entry.terminalId()
                                .equals(terminal.ref().deviceId())).isPresent()) {
                            reg.renameScreen(payload.id(), payload.name());
                            yield true;
                        }
                        yield false;
                    }
                    case DeviceActionC2SPayload.ACTION_REMOVE -> {
                        if (payload.targetType() == DeviceActionC2SPayload.TYPE_CAMERA
                                && reg.camera(payload.id()).filter(entry -> entry.terminalId()
                                .equals(terminal.ref().deviceId())).isPresent()) {
                            yield reg.removeCamera(payload.id());
                        }
                        if (payload.targetType() == DeviceActionC2SPayload.TYPE_SCREEN
                                && reg.screen(payload.id()).filter(entry -> entry.terminalId()
                                .equals(terminal.ref().deviceId())).isPresent()) {
                            yield reg.removeScreen(payload.id());
                        }
                        yield false;
                    }
                    default -> false;
                };
            } catch (IllegalArgumentException | java.util.NoSuchElementException ignored) {
                // Invalid or stale client reference; ignore the mutation.
            }
            if (ok) {
                DeviceSubscriptions.broadcast(server, payload.terminal().deviceId());
            }
        });

        // --- subscribe/unsubscribe to a terminal's snapshot stream ---
        ServerPlayNetworking.registerGlobalReceiver(DeviceSubscribeC2SPayload.ID,
                (payload, ctx) -> {
                    var player = ctx.player();
                    var registry = GlobalDeviceRegistry.get(player.getServer());
                    var terminal = registry.terminal(payload.terminal().deviceId())
                            .filter(entry -> entry.ref().equals(payload.terminal()));
                    if (terminal.isEmpty()) {
                        return;
                    }
                    if (payload.subscribe()) {
                        DeviceSubscriptions.subscribe(payload.terminal().deviceId(), player.getUuid());
                        ServerPlayNetworking.send(player, DeviceSubscriptions.buildSnapshot(
                                player.getServer(), payload.terminal(), false));
                    } else {
                        DeviceSubscriptions.unsubscribe(payload.terminal().deviceId(), player.getUuid());
                    }
                });

        // --- clean up stale subscriptions on logout ---
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DeviceSubscriptions.unsubscribeAll(handler.getPlayer().getUuid()));
    }
}
