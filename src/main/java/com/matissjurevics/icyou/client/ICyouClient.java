package com.matissjurevics.icyou.client;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.client.CameraViewController;
import com.matissjurevics.icyou.client.render.ScreenFeedRenderer;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;
import com.matissjurevics.icyou.network.FeedDataS2CPayload;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;

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

        ClientPlayNetworking.registerGlobalReceiver(FeedDataS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.world != null && client.world.getBlockEntity(
                        payload.screenPos()) instanceof ScreenBlockEntity screen) {
                    screen.updateClientBlips(payload.blips());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EnterCameraViewS2CPayload.ID,
                (payload, context) -> MinecraftClient.getInstance().execute(() ->
                        CameraViewController.enter(payload.camPos(),
                                Direction.byId(payload.facingId()))));

        CameraViewController.init();

        ICyouMod.LOGGER.info("ICyou client initialized");
    }
}
