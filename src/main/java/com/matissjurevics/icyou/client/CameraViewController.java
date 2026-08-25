package com.matissjurevics.icyou.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Owns the "viewing a security camera" state. While active:
 * - the camera mixin anchors the view to the security camera,
 * - mouse look pans/tilts that camera remotely (body frozen, movement locked),
 * - sneaking ends the feed.
 */
public final class CameraViewController {

    private CameraViewController() {}

    /** Immutable snapshot of the overridden camera pose, read by the mixin. */
    public record View(Vec3d pos, float yaw, float pitch, Direction facing) {}

    private static List<EnterCameraViewS2CPayload.CamRef> targets = List.of();
    private static int targetIndex;

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

    /**
     * Starts (or switches) the detached feed. Called from the network
     * receiver on the main thread; safe to call repeatedly to change channel.
     */
    public static void begin(List<EnterCameraViewS2CPayload.CamRef> cameras) {
        if (cameras.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        EnterCameraViewS2CPayload.CamRef first = cameras.get(0);

        if (active && camPos != null) {
            saveAngles(); // remember where we left the previous camera
        }
        targets = cameras;
        applyTarget(first, client);
        active = true;
    }

    private static void applyTarget(EnterCameraViewS2CPayload.CamRef ref, MinecraftClient client) {
        camPos = ref.pos().toImmutable();
        camFacing = Direction.byId(ref.facingId());
        viewPos = Vec3d.ofCenter(camPos)
                .add(new Vec3d(camFacing.getOffsetX(), 0, camFacing.getOffsetZ()).multiply(0.2))
                .add(0, -0.1, 0);

        float[] saved = REMEMBERED_ANGLES.get(camPos);
        viewYaw = saved != null ? saved[0] : yawFor(camFacing);
        // Horizontal cameras begin level with their block-facing direction.
        // Mouse tilt is still remembered after the user adjusts the view.
        viewPitch = saved != null ? saved[1] : pitchFor(camFacing);

        if (!isActive()) {
            // Fresh entry: freeze the player's body where they stand.
            if (client.player != null) {
                bodyYaw = client.player.getYaw();
                bodyPitch = client.player.getPitch();
            }
            client.gameRenderer.setRenderHand(false);
        }
    }

    /** Advances to the next camera in the current target list (if several). */
    public static void cycleTarget() {
        if (targets.size() < 2) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        saveAngles();
        targetIndex = Math.floorMod(targetIndex + 1, targets.size());
        applyTarget(targets.get(targetIndex), client);
    }

    public static void exit() {
        if (!active) {
            return;
        }
        saveAngles();
        active = false;
        camPos = null;
        targets = List.of();
        targetIndex = 0;
        MinecraftClient.getInstance().gameRenderer.setRenderHand(true);
    }

    /** Registers the tick + HUD hooks. Called from {@code ICyouClient}. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isActive()) {
                return;
            }
            PlayerEntity player = client.player;
            if (player == null || client.world == null) {
                exit();
                return;
            }

            routeMouseLookToCamera(player);

            // Lock movement: cancel any velocity WASD/gravity accumulated.
            player.setVelocity(0, 0, 0);

            // Sneak leaves the feed.
            while (client.options.sneakKey.wasPressed()) {
                exit();
            }
            if (!isActive() || camPos == null) {
                return;
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
    private static void routeMouseLookToCamera(PlayerEntity player) {
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

        String channel = targets.size() > 1
                ? String.format("CAM %d/%d", targetIndex + 1, targets.size())
                : "CAM";
        context.drawTextWithShadow(client.textRenderer,
                Text.literal(channel + " [" + camFacing.asString() + "]"), 6, 4, 0xFF60FF60);
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

    private static float pitchFor(Direction facing) {
        return switch (facing) {
            case UP -> -90.0f;
            case DOWN -> 90.0f;
            default -> 0.0f;
        };
    }
}
