package com.matissjurevics.icyou.client;

import java.util.HashMap;
import java.util.Map;

import com.matissjurevics.icyou.camera.CameraBlock;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Owns the "viewing a security camera" state. While active, the camera mixin
 * anchors the player's view to the linked security camera; mouse look pans and
 * tilts that camera remotely (the player's body stays frozen in place), and
 * sneaking ends the feed.
 */
public final class CameraViewController {

    private CameraViewController() {}

    /** Immutable snapshot of the overridden camera pose, read by the mixin. */
    public record View(Vec3d pos, float yaw, float pitch, Direction facing) {}

    private static boolean active;
    private static BlockPos camPos;
    private static Vec3d viewPos;
    private static Direction camFacing = Direction.NORTH;
    private static float viewYaw, viewPitch;

    /** Pan/tilt remembered per camera for this session (client-side only). */
    private static final Map<BlockPos, float[]> REMEMBERED_ANGLES = new HashMap<>();

    /** The player's body rotation is frozen here while viewing. */
    private static float bodyYaw, bodyPitch;

    public static View current() {
        return active ? new View(viewPos, viewYaw, viewPitch, camFacing) : null;
    }

    public static boolean isActive() {
        return active;
    }

    public static void enter(BlockPos cameraPos, Direction facing) {
        camPos = cameraPos.toImmutable();
        camFacing = facing;
        viewPos = Vec3d.ofCenter(cameraPos)
                .add(new Vec3d(facing.getOffsetX(), 0, facing.getOffsetZ()).multiply(0.2))
                .add(0, -0.1, 0);

        float[] saved = REMEMBERED_ANGLES.get(camPos);
        viewYaw = saved != null ? saved[0] : yawFor(facing);
        viewPitch = saved != null ? saved[1] : -20.0f;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            bodyYaw = client.player.getYaw();
            bodyPitch = client.player.getPitch();
        }
        client.gameRenderer.setRenderHand(false);
        active = true;
    }

    public static void exit() {
        if (!isActive()) {
            return;
        }
        saveAngles();
        active = false;
        camPos = null;
        MinecraftClient.getInstance().gameRenderer.setRenderHand(true);
    }

    /** Registers the tick + HUD hooks. Called from {@code ICyouClient}. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isActive()) {
                return;
            }
            if (client.player == null || client.world == null) {
                exit();
                return;
            }

            routeMouseLookToCamera(client);

            // Sneak leaves the feed.
            while (client.options.sneakKey.wasPressed()) {
                exit();
            }
            // Signal lost if the camera block disappears.
            if (!(client.world.getBlockState(camPos).getBlock() instanceof CameraBlock)) {
                exit();
            }
        });

        HudRenderCallback.EVENT.register(CameraViewController::drawOverlay);
    }

    /**
     * Mouse input keeps rotating the (invisible) player body as usual; each
     * tick we harvest that rotation as pan/tilt deltas for the remote camera
     * and then reset the body, so the player doesn't spin around physically.
     */
    private static void routeMouseLookToCamera(MinecraftClient client) {
        var player = client.player;
        float deltaYaw = player.getYaw() - bodyYaw;
        float deltaPitch = player.getPitch() - bodyPitch;

        // Yaw wraps at ±180° — normalise the delta so it doesn't jump.
        deltaYaw = MathHelper.wrapDegrees(deltaYaw);

        viewYaw = MathHelper.wrapDegrees(viewYaw + deltaYaw);
        viewPitch = MathHelper.clamp(viewPitch + deltaPitch, -90.0f, 90.0f);
        saveAngles();

        player.setYaw(bodyYaw);
        player.setPitch(bodyPitch);
        player.prevYaw = bodyYaw;
        player.prevPitch = bodyPitch;
    }

    private static void saveAngles() {
        if (camPos != null) {
            REMEMBERED_ANGLES.put(camPos, new float[] {viewYaw, viewPitch});
        }
    }

    private static void drawOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (!isActive()) {
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
                Text.literal("CAM [" + camFacing.asString() + "]"), 6, 4, 0xFF60FF60);
        context.drawTextWithShadow(client.textRenderer,
                Text.literal(String.format("YAW %4d  PIT %3d",
                        (int) viewYaw, (int) viewPitch)),
                6, bar + 2, 0xFFB0FFB0);
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("mouse: pan/tilt   sneak: exit"), w / 2, h - bar - 10,
                0xFFB0B0B0);
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
