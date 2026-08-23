package com.matissjurevics.icyou.client;

import com.matissjurevics.icyou.ICyouMod;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint. Everything that touches rendering, GUIs or other
 * {@code net.minecraft.client} classes must live below this package so the
 * mod keeps working on dedicated servers.
 */
public class ICyouClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ICyouMod.LOGGER.info("ICyou client initialized");
    }
}
