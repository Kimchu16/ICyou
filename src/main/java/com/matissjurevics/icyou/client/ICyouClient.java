package com.matissjurevics.icyou.client;

import java.util.ArrayList;
import java.util.List;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.client.gui.TerminalGuiScreen;
import com.matissjurevics.icyou.client.agent.ClientRenderAgentLifecycle;
import com.matissjurevics.icyou.client.hud.WirelessHud;
import com.matissjurevics.icyou.client.render.RttFeedManager;
import com.matissjurevics.icyou.client.render.ScreenFeedRenderer;
import com.matissjurevics.icyou.network.DeviceSnapshotS2CPayload;
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
 * Client-only entrypoint. Rendering, GUIs and HUDs live below this package so
 * the mod keeps working on dedicated servers.
 */
public class ICyouClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(
                ModBlockEntities.SCREEN, ScreenFeedRenderer::new);

        // Live feed frames for screen panels.
        ClientPlayNetworking.registerGlobalReceiver(FeedDataS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.world != null
                        && client.world.getRegistryKey().equals(payload.screen().dimension())
                        && client.world.getBlockEntity(payload.screen().position())
                        instanceof ScreenBlockEntity screen) {
                    screen.updateClientFeed(payload.blips(), payload.camera(),
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

        // Device snapshots: refresh cache, open GUI when requested.
        ClientPlayNetworking.registerGlobalReceiver(DeviceSnapshotS2CPayload.ID,
                (payload, context) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> {
                        ClientDeviceCache.update(payload);
                        if (payload.openGui() && client.currentScreen == null) {
                            client.setScreen(new TerminalGuiScreen(payload));
                        }
                    });
                });

        CameraViewController.init();
        WirelessHud.init();
        ClientTickEvents.END_CLIENT_TICK.register(RttFeedManager::tick);
        ClientRenderAgentLifecycle.register();

        ICyouMod.LOGGER.info("ICyou client initialized");
    }
}
