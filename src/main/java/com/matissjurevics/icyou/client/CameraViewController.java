package com.matissjurevics.icyou.client;

import com.matissjurevics.icyou.camera.CameraBlock;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Owns the "viewing a security camera" state. While active, the camera mixin
 * anchors the player's view to the linked camera; the player stays where they
 * physically are and can leave by sneaking.
 */
public final class CameraViewController {

    private CameraViewController() {}

    /** Immutable snapshot of the overridden camera pose. */
    public record View(Vec3d pos, float yaw, float pitch, Direction facing) {}

    private static View active;
    private static BlockPos camPos;

    public static View current() {
        return active;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static void enter(BlockPos cameraPos, Direction facing) {
        camPos = cameraPos.toImmutable();
        Vec3d center = Vec3d.ofCenter(cameraPos).add(
                new Vec3d(facing.getOffsetX(), 0, facing.getOffsetZ()).multiply(0.2));
        active = new View(center.add(0, -0.1, 0), yawFor(facing), -20.0f, facing);
        MinecraftClient.getInstance().gameRenderer.setRenderHand(false);
    }

    public static void exit() {
        if (!isActive()) {
            return;
        }
        active = null;
        camPos = null;
        MinecraftClient.getInstance().gameRenderer.setRenderHand(true);
    }

    /** Registers the tick + HUD hooks. Called from {@code ICyouClient}. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isActive()) {
                return;
            }
            // Sneak leaves the feed.
            while (client.options.sneakKey.wasPressed()) {
                exit();
            }
            // Signal lost if the camera block disappears or world unloads.
            if (camPos != null && client.world != null
                    && !(client.world.getBlockState(camPos).getBlock() instanceof CameraBlock)) {
                exit();
            }
        });

        HudRenderCallback.EVENT.register(CameraViewController::drawOverlay);
    }

    private static void drawOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (!isActive() || active == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();

        // Simple vignette strips, CCTV style.
        int bar = 14;
        context.fill(0, 0, w, bar, 0x88000000);
        context.fill(0, h - bar, w, h, 0x88000000);

        boolean blink = (System.currentTimeMillis() % 1000) < 600;
        int dot = blink ? 0xFFFF3030 : 0xFF702020;
        context.fill(w - 60, 4, w - 54, 10, dot);
        context.drawTextWithShadow(client.textRenderer,
                Text.literal("LIVE"), w - 48, 4, 0xFFFFFFFF);

        context.drawTextWithShadow(client.textRenderer,
                Text.literal("CAM [" + active.facing().asString() + "]"), 6, 4, 0xFF60FF60);
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("sneak to exit"), w / 2, h - bar - 10, 0xFFB0B0B0);
    }

    private static float yawFor(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0f;   // MC yaw 0 = south
            case EAST -> -90.0f;
            case WEST -> 90.0f;
            default -> 0.0f;
        };
    }
}
