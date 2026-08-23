package com.matissjurevics.icyou.client;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.client.render.ScreenFeedRenderer;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

/**
 * Client-only entrypoint. Everything that touches rendering, GUIs or other
 * {@code net.minecraft.client} classes must live below this package so the
 * mod keeps working on dedicated servers.
 */
public class ICyouClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(
                ModBlockEntities.SCREEN, ScreenFeedRenderer::new);

        ICyouMod.LOGGER.info("ICyou client initialized");
    }
}
