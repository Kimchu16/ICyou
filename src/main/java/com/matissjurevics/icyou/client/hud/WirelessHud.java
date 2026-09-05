package com.matissjurevics.icyou.client.hud;

import java.util.List;

import com.matissjurevics.icyou.client.CameraViewController;
import com.matissjurevics.icyou.client.ClientDeviceCache;
import com.matissjurevics.icyou.device.TerminalRef;
import com.matissjurevics.icyou.network.DeviceSnapshotS2CPayload;
import com.matissjurevics.icyou.network.DeviceSubscribeC2SPayload;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

/**
 * The portable screen's HUD: a list of every camera on the paired terminal.
 * Press 1-8 to detach-view a camera; sneak closes the list.
 */
public final class WirelessHud {

    private WirelessHud() {}

    private static boolean open;
    private static TerminalRef terminal;

    public static boolean isOpen() {
        return open;
    }

    public static void toggle(TerminalRef terminalRef) {
        if (open) {
            close();
        } else {
            open = true;
            terminal = terminalRef;
            ClientPlayNetworking.send(new DeviceSubscribeC2SPayload(terminal, true));
        }
    }

    public static void close() {
        if (open) {
            ClientPlayNetworking.send(new DeviceSubscribeC2SPayload(terminal, false));
        }
        open = false;
        terminal = null;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!open) {
                return;
            }
            // Sneak closes the list (unless we're currently inside a camera view).
            while (client.options.sneakKey.wasPressed() && !CameraViewController.isActive()) {
                close();
            }
            if (!open) {
                return;
            }
            // Number keys 1-8 select a camera to view.
            var snapshot = ClientDeviceCache.get();
            if (snapshot == null || client.currentScreen != null) {
                return;
            }
            List<DeviceSnapshotS2CPayload.Cam> cams = snapshot.cameras();
            long handle = client.getWindow().getHandle();
            for (int i = 0; i < Math.min(8, cams.size()); i++) {
                if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_1 + i)) {
                    var cam = cams.get(i);
                    if (cam.online()) {
                        CameraViewController.begin(List.of(new EnterCameraViewS2CPayload.CamRef(
                                cam.ref(), cam.facingId())));
                    }
                    break;
                }
            }
        });

        HudRenderCallback.EVENT.register(WirelessHud::draw);
    }

    private static void draw(DrawContext context, RenderTickCounter tickCounter) {
        if (!open) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        DeviceSnapshotS2CPayload snapshot = ClientDeviceCache.get();
        if (snapshot == null) {
            return;
        }
        List<DeviceSnapshotS2CPayload.Cam> cams = snapshot.cameras();

        int x = 8;
        int y = 40;
        int width = 170;
        int rowH = 12;

        context.fill(x - 4, y - 12, x + width, y + (cams.size() + 1) * rowH + 4, 0xC0000000);
        context.drawTextWithShadow(client.textRenderer, Text.literal("CAMERAS"),
                x, y - 10, 0xFF60FF60);
        if (cams.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, Text.literal("(none linked)"),
                    x, y + rowH, 0xFF909090);
        } else {
            for (int i = 0; i < cams.size(); i++) {
                var cam = cams.get(i);
                String key = i < 9 ? (i + 1) + " " : "  ";
                String line = key + cam.name() + (cam.online() ? "" : "  [OFFLINE]");
                int color = cam.online() ? 0xFFFFFFFF : 0xFF707070;
                context.drawTextWithShadow(client.textRenderer, Text.literal(line),
                        x, y + i * rowH, color);
            }
        }
        context.drawTextWithShadow(client.textRenderer,
                Text.literal("sneak to close"), x, y + (cams.size() + 1) * rowH - 4, 0xFF909090);
    }
}
