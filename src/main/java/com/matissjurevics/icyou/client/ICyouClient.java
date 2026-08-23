package com.matissjurevics.icyou.client;

import java.util.ArrayList;
import java.util.List;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.client.CameraViewController;
import com.matissjurevics.icyou.client.render.RttFeedManager;
import com.matissjurevics.icyou.client.render.ScreenFeedRenderer;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;
import com.matissjurevics.icyou.network.FeedDataS2CPayload;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.MinecraftClient;

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

        // Live feed snapshots for screen panels.
        ClientPlayNetworking.registerGlobalReceiver(FeedDataS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.world != null && client.world.getBlockEntity(
                        payload.screenPos()) instanceof ScreenBlockEntity screen) {
                    screen.updateClientFeed(payload.blips(), payload.camPos(),
                            payload.facingId(), payload.index(), payload.count());
                }
            });
        });

        // Detached camera view (portable screen).
        ClientPlayNetworking.registerGlobalReceiver(EnterCameraViewS2CPayload.ID,
                (payload, context) -> {
                    List<EnterCameraViewS2CPayload.CamRef> refs = new ArrayList<>(
                            payload.cameras());
                    MinecraftClient.getInstance().execute(() ->
                            CameraViewController.begin(refs));
                });

        CameraViewController.init();
        ClientTickEvents.END_CLIENT_TICK.register(RttFeedManager::tick);

        ICyouMod.LOGGER.info("ICyou client initialized");
    }
}
