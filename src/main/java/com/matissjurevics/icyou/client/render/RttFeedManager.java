package com.matissjurevics.icyou.client.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.client.stream.StreamFrame;
import com.matissjurevics.icyou.client.stream.StreamStore;
import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.stb.STBIWriteCallbackI;
import org.lwjgl.system.MemoryUtil;

/**
 * Renders security cameras with Minecraft's real {@link net.minecraft.client.render.WorldRenderer}
 * into GPU framebuffers. One feed is shared by every screen assigned to the same camera.
 */
public final class RttFeedManager {

    private RttFeedManager() {}

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    private static final float FOV_DEGREES = 70.0f;
    private static final long FRAME_INTERVAL_MS = 62L;
    private static final long STALE_MS = 3000L;
    private static final double SCREEN_VIEW_DISTANCE = 64.0;
    private static final int MAX_FAILURES = 5;

    private static final Map<BlockPos, ScreenBlockEntity> SCREENS = new LinkedHashMap<>();
    private static final Map<UUID, CameraFeed> FEEDS = new LinkedHashMap<>();

    private static boolean renderingFeed;
    private static boolean disabled;
    private static int failureCount;
    private static ByteBuffer captureBuf;

    private static final class CameraFeed {
        final UUID cameraId;
        final BlockPos cameraPos;
        final Identifier textureId;
        final SimpleFramebuffer framebuffer;
        Direction facing;
        long lastRender;

        CameraFeed(MinecraftClient client, CameraRef camera, Direction facing) {
            this.cameraId = camera.deviceId();
            this.cameraPos = camera.position();
            this.facing = facing;
            this.framebuffer = new SimpleFramebuffer(
                    WIDTH, HEIGHT, true, MinecraftClient.IS_SYSTEM_MAC);
            this.framebuffer.setTexFilter(GL11.GL_LINEAR);
            this.textureId = Identifier.of(ICyouMod.MOD_ID,
                    "camera_feed_" + cameraId.toString().replace("-", ""));
            client.getTextureManager().registerTexture(textureId,
                    new FramebufferTexture(framebuffer.getColorAttachment()));
        }

        void close(MinecraftClient client) {
            client.getTextureManager().destroyTexture(textureId);
            framebuffer.delete();
        }
    }

    private record CameraTarget(CameraRef ref, Direction facing) {
    }

    /** Texture-manager adapter for an FBO-owned color attachment. */
    private static final class FramebufferTexture extends AbstractTexture {
        FramebufferTexture(int colorAttachment) {
            this.glId = colorAttachment;
        }

        @Override
        public void load(ResourceManager manager) throws IOException {
            // The framebuffer has already allocated and populated this texture.
        }

        @Override
        public void clearGlId() {
            // TextureManager removes its reference; the framebuffer owns deletion.
            this.glId = -1;
        }
    }

    /** Exposes Camera's protected pose setters for the independent feed camera. */
    private static final class FeedCamera extends Camera {
        void configure(MinecraftClient client, Vec3d pos, float yaw, float pitch,
                       float tickDelta) {
            // Camera requires a focused entity, so reuse the local player for
            // initialization. Mark this as an external camera: vanilla omits
            // the focused entity from a first-person pass, which otherwise
            // makes the player standing in front of the security camera vanish.
            update(client.world, client.player, true, false, tickDelta);
            setPos(pos);
            setRotation(yaw, pitch);
        }
    }

    /** Called by each loaded screen's client block-entity ticker. */
    public static void track(ScreenBlockEntity screen) {
        if (!disabled && !screen.isRemoved()) {
            SCREENS.put(screen.getPos().toImmutable(), screen);
        }
    }

    /** Lightweight lifecycle cleanup; rendering itself is frame-driven. */
    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            clear(client);
            return;
        }
        pruneScreens(client);
    }

    /** Called by GameRendererMixin once per normal world-rendered frame. */
    public static void renderFrame(MinecraftClient client, GameRenderer gameRenderer,
                                   RenderTickCounter tickCounter) {
        if (disabled || renderingFeed || client.world == null || client.player == null) {
            return;
        }

        pruneScreens(client);
        Map<UUID, CameraTarget> active = collectActiveCameras(client);
        reconcileFeeds(client, active);
        if (active.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        CameraFeed next = active.entrySet().stream()
                .map(entry -> FEEDS.computeIfAbsent(entry.getKey(), key ->
                        new CameraFeed(client, entry.getValue().ref(), entry.getValue().facing())))
                .peek(feed -> feed.facing = active.get(feed.cameraId).facing())
                .filter(feed -> now - feed.lastRender >= FRAME_INTERVAL_MS)
                .min(Comparator.comparingLong(feed -> feed.lastRender))
                .orElse(null);
        if (next != null) {
            renderCamera(client, gameRenderer, tickCounter, next, now);
        }
    }

    private static Map<UUID, CameraTarget> collectActiveCameras(MinecraftClient client) {
        Map<UUID, CameraTarget> active = new LinkedHashMap<>();
        double maxDistanceSq = SCREEN_VIEW_DISTANCE * SCREEN_VIEW_DISTANCE;
        for (ScreenBlockEntity screen : SCREENS.values()) {
            CameraRef camera = screen.getLastCameraRef();
            if (camera == null || !camera.dimension().equals(client.world.getRegistryKey())
                    || screen.getWorld() != client.world
                    || client.player.squaredDistanceTo(Vec3d.ofCenter(screen.getPos()))
                    > maxDistanceSq) {
                continue;
            }
            active.put(camera.deviceId(), new CameraTarget(
                    camera, Direction.byId(screen.getLastFacingId())));
        }
        return active;
    }

    private static void reconcileFeeds(MinecraftClient client,
                                       Map<UUID, CameraTarget> active) {
        Set<UUID> retained = new HashSet<>(active.keySet());
        Iterator<Map.Entry<UUID, CameraFeed>> iterator = FEEDS.entrySet().iterator();
        while (iterator.hasNext()) {
            CameraFeed feed = iterator.next().getValue();
            if (!retained.contains(feed.cameraId)) {
                feed.close(client);
                iterator.remove();
            }
        }
    }

    private static void renderCamera(MinecraftClient client, GameRenderer gameRenderer,
                                     RenderTickCounter tickCounter, CameraFeed feed,
                                     long now) {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        float tickDelta = tickCounter.getTickDelta(true);
        FeedCamera camera = new FeedCamera();
        Vec3d cameraPos = cameraPosition(feed.cameraPos, feed.facing);
        camera.configure(client, cameraPos, yawFor(feed.facing), pitchFor(feed.facing),
                tickDelta);

        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(FOV_DEGREES), (float) WIDTH / HEIGHT,
                0.05f, gameRenderer.getFarPlaneDistance());
        Quaternionf inverseRotation = camera.getRotation().conjugate(new Quaternionf());
        Matrix4f view = new Matrix4f().rotation(inverseRotation);

        renderingFeed = true;
        try (OffscreenRenderContext.Scope ignored =
                     OffscreenRenderContext.enter(feed.framebuffer)) {
            feed.framebuffer.setClearColor(0.01f, 0.015f, 0.02f, 1.0f);
            // Framebuffer.clear() binds and then unbinds its target internally.
            // Bind for the world pass only after clearing, or the world is drawn
            // to the default target while this texture remains black.
            feed.framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            feed.framebuffer.beginWrite(true);
            gameRenderer.loadProjectionMatrix(projection);

            client.worldRenderer.setupFrustum(cameraPos, view, projection);
            client.worldRenderer.render(tickCounter, false, camera, gameRenderer,
                    gameRenderer.getLightmapTextureManager(), view, projection);
            renderEntities(client, feed, camera, cameraPos, view, tickDelta);
            feed.lastRender = now;
            publishFeed(feed);
            failureCount = 0;
        } catch (Throwable error) {
            failureCount++;
            ICyouMod.LOGGER.error("Secondary camera render failed ({}/{}) for {}",
                    failureCount, MAX_FAILURES, feed.cameraPos, error);
            if (failureCount >= MAX_FAILURES) {
                disabled = true;
                ICyouMod.LOGGER.warn("Secondary camera feeds disabled after repeated failures");
            }
        } finally {
            renderingFeed = false;
            feed.framebuffer.endWrite();
            client.getFramebuffer().beginWrite(true);
            gameRenderer.loadProjectionMatrix(savedProjection);
        }
    }

    /**
     * WorldRenderer's entity stage uses several vanilla render targets that are
     * owned by the main player renderer. Draw loaded entities once more into the
     * camera FBO so their normal models, animations, skins and equipment share
     * the terrain depth buffer instead of disappearing from the feed.
     */
    private static void renderEntities(MinecraftClient client, CameraFeed feed,
                                       FeedCamera camera, Vec3d cameraPos,
                                       Matrix4f view, float tickDelta) {
        // Vanilla's nested targets can leave a window-sized viewport active.
        // Restore both the camera FBO and its 320x180 viewport before entities.
        feed.framebuffer.beginWrite(true);

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        dispatcher.configure(client.world, camera, null);
        VertexConsumerProvider.Immediate consumers =
                client.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = new MatrixStack();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(view);
        RenderSystem.applyModelViewMatrix();

        double maxDistanceSq = gameEntityDistanceSq(client);
        try {
            for (Entity entity : client.world.getEntities()) {
                if (entity.isRemoved()
                        || entity.squaredDistanceTo(cameraPos) > maxDistanceSq) {
                    continue;
                }

                double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
                double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
                double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
                float yaw = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
                int light = dispatcher.getLight(entity, tickDelta);

                dispatcher.render(entity, x - cameraPos.x, y - cameraPos.y,
                        z - cameraPos.z, yaw, tickDelta, matrices, consumers, light);
            }
            consumers.draw();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static double gameEntityDistanceSq(MinecraftClient client) {
        double distance = Math.max(32.0,
                client.options.getClampedViewDistance() * 16.0);
        return distance * distance;
    }

    /** Reads the just-rendered FBO and publishes a JPEG frame for web streaming. */
    private static void publishFeed(CameraFeed feed) {
        try {
            if (captureBuf == null || captureBuf.capacity() < WIDTH * HEIGHT * 4) {
                captureBuf = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
            }
            captureBuf.clear();
            GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, captureBuf);
            byte[] jpg = encodeJpeg(captureBuf, WIDTH, HEIGHT);
            if (jpg != null && jpg.length > 0) {
                StreamStore.put(feed.cameraId,
                        new StreamFrame(jpg, System.currentTimeMillis()));
            }
        } catch (Throwable t) {
            ICyouMod.LOGGER.debug("[stream] capture failed for {}", feed.cameraPos, t);
        }
    }

    /** Row-flips the GL framebuffer and encodes an in-memory JPEG. */
    private static byte[] encodeJpeg(ByteBuffer rgba, int w, int h) {
        ByteBuffer flipped = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            int src = y * w * 4;
            int dst = (h - 1 - y) * w * 4;
            for (int x = 0; x < w * 4; x++) {
                flipped.put(dst + x, rgba.get(src + x));
            }
        }
        ByteBuffer out = ByteBuffer.allocateDirect(96 * 1024);
        int[] off = new int[1];
        STBIWriteCallbackI cb = (user, data, size) -> {
            ByteBuffer chunk = MemoryUtil.memByteBuffer(data, size);
            for (int i = 0; i < size; i++) {
                out.put(off[0] + i, chunk.get(i));
            }
            off[0] += size;
        };
        int ok = STBImageWrite.stbi_write_jpg_to_func(cb, 0L, w, h, 4, flipped, 80);
        if (ok == 0) {
            return null;
        }
        byte[] jpg = new byte[off[0]];
        for (int i = 0; i < off[0]; i++) {
            jpg[i] = out.get(i);
        }
        return jpg;
    }

    private static Vec3d cameraPosition(BlockPos pos, Direction facing) {
        return Vec3d.ofCenter(pos)
                .add(Vec3d.of(facing.getVector()).multiply(0.65))
                .add(0.0, -0.1, 0.0);
    }

    private static float yawFor(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0f;
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

    private static void pruneScreens(MinecraftClient client) {
        SCREENS.entrySet().removeIf(entry -> {
            ScreenBlockEntity screen = entry.getValue();
            return screen.isRemoved() || screen.getWorld() != client.world;
        });
    }

    private static void clear(MinecraftClient client) {
        SCREENS.clear();
        List<CameraFeed> feeds = new ArrayList<>(FEEDS.values());
        FEEDS.clear();
        feeds.forEach(feed -> feed.close(client));
    }

    public static boolean isRenderingFeed() {
        return renderingFeed;
    }

    /** Used by WorldRendererMixin to keep every vanilla sub-pass in the feed FBO. */
    public static Framebuffer currentRenderTarget() {
        return renderingFeed ? OffscreenRenderContext.target() : null;
    }

    public static boolean hasLiveFeed(ScreenBlockEntity screen) {
        CameraFeed feed = feedFor(screen);
        return feed != null && System.currentTimeMillis() - feed.lastRender < STALE_MS;
    }

    public static Identifier textureIdFor(ScreenBlockEntity screen) {
        CameraFeed feed = feedFor(screen);
        return feed == null ? null : feed.textureId;
    }

    private static CameraFeed feedFor(ScreenBlockEntity screen) {
        CameraRef camera = screen.getLastCameraRef();
        return camera == null ? null : FEEDS.get(camera.deviceId());
    }
}
