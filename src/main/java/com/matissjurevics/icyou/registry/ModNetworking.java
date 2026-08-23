package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.network.FeedDataS2CPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** All custom network payloads and their codecs. */
public final class ModNetworking {

    private ModNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                FeedDataS2CPayload.ID, FeedDataS2CPayload.CODEC);
    }
}
