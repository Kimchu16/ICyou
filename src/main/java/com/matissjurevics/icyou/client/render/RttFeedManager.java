package com.matissjurevics.icyou.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.camera.CameraViews;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * True render-to-texture feeds: every tick, ONE paired screen gets its frame
 * refreshed — living entities near that screen's selected camera are rendered
 * (with their real models/skins) from the camera's pose into a small offscreen
 * framebuffer, read back to CPU, and published as a dynamic texture the screen
 * BER draws onto the panel.
 *
 * <p>Round-robin keeps GPU cost flat regardless of how many screens exist.
 * Terrain is intentionally not rendered (dark CCTV backdrop instead) — adding
 * chunk geometry would require portal-engine-level second-pass machinery.</p>
 */
public final class RttFeedManager {

    private RttFeedManager() {}

    private static final int SIZE = 96;
    private static final long STALE_MS = 2500;
    private static final double VIEW_DISTANCE = 48.0;
    private static final float FOV_DEGREES = 70.0f;
    private static final int MAX_FAILURES = 5;

    private static final class Channel {
        final ScreenBlockEntity be;
        final Identifier textureId;
        final NativeImageBackedTexture texture;
        final NativeImage image;
        long lastUpdate;

        Channel(ScreenBlockEntity be, Identifier textureId,
                NativeImageBackedTexture texture, NativeImage image) {
            this.be = be;
            this.textureId = textureId;
            this.texture = texture;
            this.image = image;
        }
    }

    /** Insertion-ordered so round-robin iteration is stable. */
    private static final Map<BlockPos, Channel> CHANNELS = new LinkedHashMap<>();
    private static SimpleFramebuffer framebuffer;
    private static ByteBuffer pixelBuffer;
    private static int failureCount;
    private static boolean disabled;

    /** Registers/refreshes a tracked screen. Called from the client ticker. */
    public static void track(ScreenBlockEntity be) {
        if (disabled || be.isRemoved() || CHANNELS.containsKey(be.getPos())) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier id = Identifier.of(ICyouMod.MOD_ID, "rtt_" + be.getPos().asLong());
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        client.getTextureManager().registerTexture(id, texture);
        CHANNELS.put(be.getPos(), new Channel(be, id, texture, image));
    }

    /** Round-robin refresh: one screen per tick. */
    public static void tick(MinecraftClient client) {
        if (disabled || client.world == null || CHANNELS.isEmpty()) {
            return;
        }

        // Prune dead entries.
        Iterator<Map.Entry<BlockPos, Channel>> it = CHANNELS.entrySet().iterator();
        while (it.hasNext()) {
            Channel ch = it.next().getValue();
            if (ch.be.isRemoved() || ch.be.getWorld() != client.world) {
                client.getTextureManager().destroyTexture(ch.textureId);
                it.remove();
            }
        }
        if (CHANNELS.isEmpty()) {
            return;
        }

        // Round-robin: rotate once, then take the first eligible channel.
        rotate();

        Channel candidate = firstValue();
        long minGap = STALE_MS / Math.max(1, CHANNELS.size());
        if (candidate == null
                || System.currentTimeMillis() - candidate.lastUpdate < minGap
                || candidate.be.getLastCamPos() == null
                || candidate.be.getWorld() != client.world
                || client.player == null
                || client.player.squaredDistanceTo(
                        candidate.be.getPos().getX() + 0.5,
                        candidate.be.getPos().getY() + 0.5,
                        candidate.be.getPos().getZ() + 0.5)
                        > VIEW_DISTANCE * VIEW_DISTANCE) {
            return; // nothing eligible this tick
        }
        renderInto(client, candidate);
    }

    private static void rotate() {
        if (CHANNELS.isEmpty()) {
            return;
        }
        Map.Entry<BlockPos, Channel> first = CHANNELS.entrySet().iterator().next();
        CHANNELS.remove(first.getKey());
        CHANNELS.put(first.getKey(), first.getValue());
    }

    private static Channel firstValue() {
        return CHANNELS.isEmpty() ? null : CHANNELS.values().iterator().next();
    }

    private static void renderInto(MinecraftClient client, Channel channel) {
        try {
            BlockPos camPos = channel.be.getLastCamPos();
            Direction facing = Direction.byId(channel.be.getLastFacingId());
            Vec3d origin = Vec3d.ofCenter(camPos)
                    .add(new Vec3d(facing.getOffsetX(), 0, facing.getOffsetZ()).multiply(0.2))
                    .add(0, -0.1, 0);
            float yaw = switch (facing) {
                case NORTH -> 180.0f;
                case EAST -> -90.0f;
                case WEST -> 90.0f;
                default -> 0.0f;
            };
            float pitch = -20.0f;

            ensureBuffers();

            // --- render pass into the offscreen framebuffer ---
            framebuffer.beginWrite(true);
            framebuffer.setClearColor(0.01f, 0.04f, 0.02f, 1.0f);
            framebuffer.clear(false);

            MatrixStack matrices = new MatrixStack();
            matrices.multiplyPositionMatrix(new Matrix4f()
                    .perspective((float) Math.toRadians(FOV_DEGREES), 1.0f, 0.05f, 64.0f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw + 180.0f));
            matrices.translate(-origin.x, -origin.y, -origin.z);

            renderEntities(client, matrices, camPos, origin);

            // --- read back and publish ---
            pixelBuffer.clear();
            GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
            client.getFramebuffer().beginWrite(true); // restore main target

            NativeImage img = channel.image;
            for (int y = 0; y < SIZE; y++) {
                int srcRow = (SIZE - 1 - y) * SIZE; // GL origin is bottom-left
                for (int x = 0; x < SIZE; x++) {
                    int i = (srcRow + x) * 4;
                    int r = pixelBuffer.get(i) & 0xFF;
                    int g = pixelBuffer.get(i + 1) & 0xFF;
                    int b = pixelBuffer.get(i + 2) & 0xFF;
                    int a = pixelBuffer.get(i + 3) & 0xFF;
                    img.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            channel.texture.upload();
            channel.lastUpdate = System.currentTimeMillis();
        } catch (Throwable t) {
            failureCount++;
            ICyouMod.LOGGER.error("RTT feed render failed ({}/{})", failureCount, MAX_FAILURES, t);
            try {
                client.getFramebuffer().beginWrite(true);
            } catch (Throwable ignored) {}
            if (failureCount >= MAX_FAILURES) {
                disabled = true;
                ICyouMod.LOGGER.warn("RTT feeds disabled after repeated failures.");
            }
        }
    }

    /**
     * Renders every living entity inside the camera's range with its actual
     * renderer (skins, animations) at camera-relative coordinates.
     */
    private static void renderEntities(MinecraftClient client, MatrixStack matrices,
                                       BlockPos camPos, Vec3d origin) {
        float tickDelta = client.getRenderTickCounter().getTickDelta(false);
        Vec3d forward = new Vec3d(0, 0, -1);
        Box searchBox = new Box(camPos).expand(CameraViews.RANGE);

        List<LivingEntity> entities = client.world.getEntitiesByClass(
                LivingEntity.class, searchBox, e -> e != client.player && !e.isSpectator());

        VertexConsumerProvider.Immediate consumers =
                client.getBufferBuilders().getEntityVertexConsumers();

        PlayerEntity viewer = client.player;
        for (LivingEntity entity : entities) {
            Vec3d rel = entity.getBoundingBox().getCenter().subtract(origin);
            if (rel.lengthSquared() > CameraViews.RANGE * CameraViews.RANGE) {
                continue;
            }
            try {
                client.getEntityRenderDispatcher().render(
                        entity,
                        rel.x - forward.x * 0.0,
                        rel.y,
                        rel.z,
                        entity.getBodyYaw(),
                        tickDelta,
                        matrices,
                        consumers,
                        0xF000F0);
            } catch (Throwable ignored) {
                // A single bad renderer must never kill the feed.
            }
            if (viewer != null && entity == viewer) {
                break;
            }
        }
        consumers.draw(); // flush into the bound framebuffer
    }

    private static void ensureBuffers() {
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer(SIZE, SIZE, true, false);
            pixelBuffer = ByteBuffer.allocateDirect(SIZE * SIZE * 4)
                    .order(ByteOrder.nativeOrder());
        }
    }

    // --- queries used by the BER ---

    public static boolean hasLiveFeed(ScreenBlockEntity be) {
        Channel ch = CHANNELS.get(be.getPos());
        return ch != null && System.currentTimeMillis() - ch.lastUpdate < STALE_MS;
    }

    public static Identifier textureIdFor(ScreenBlockEntity be) {
        Channel ch = CHANNELS.get(be.getPos());
        return ch != null ? ch.textureId : null;
    }
}
