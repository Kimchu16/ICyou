package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.network.DeviceActionC2SPayload;
import com.matissjurevics.icyou.network.DeviceSnapshotS2CPayload;
import com.matissjurevics.icyou.network.DeviceSubscriptions;
import com.matissjurevics.icyou.network.DeviceSubscribeC2SPayload;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;
import com.matissjurevics.icyou.network.FeedDataS2CPayload;
import com.matissjurevics.icyou.terminal.DeviceRegistry;

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
            ServerWorld world = ctx.player().getServerWorld();
            DeviceRegistry reg = DeviceRegistry.get(world);
            boolean ok = switch (payload.action()) {
                case DeviceActionC2SPayload.ACTION_ASSIGN ->
                        reg.assign(payload.id(), payload.auxId());
                case DeviceActionC2SPayload.ACTION_RENAME ->
                        reg.rename(payload.targetType(), payload.id(), payload.name());
                case DeviceActionC2SPayload.ACTION_REMOVE -> {
                    switch (payload.targetType()) {
                        case DeviceActionC2SPayload.TYPE_CAMERA ->
                                reg.removeCameraById(payload.id());
                        case DeviceActionC2SPayload.TYPE_SCREEN ->
                                reg.removeScreenById(payload.id());
                        case DeviceActionC2SPayload.TYPE_WIRELESS ->
                                reg.removeWireless(payload.id());
                        default -> { }
                    }
                    yield true;
                }
                default -> false;
            };
            if (ok) {
                DeviceSubscriptions.broadcast(world, payload.terminal());
            }
        });

        // --- subscribe/unsubscribe to a terminal's snapshot stream ---
        ServerPlayNetworking.registerGlobalReceiver(DeviceSubscribeC2SPayload.ID,
                (payload, ctx) -> {
                    var player = ctx.player();
                    if (payload.subscribe()) {
                        DeviceSubscriptions.subscribe(payload.terminal(), player.getUuid());
                        ServerPlayNetworking.send(player, DeviceSubscriptions.buildSnapshot(
                                player.getServerWorld(), payload.terminal(), false));
                    } else {
                        DeviceSubscriptions.unsubscribe(payload.terminal(), player.getUuid());
                    }
                });

        // --- clean up stale subscriptions on logout ---
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DeviceSubscriptions.unsubscribeAll(handler.getPlayer().getUuid()));
    }
}
