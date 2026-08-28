package com.matissjurevics.icyou.client.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final Map<BlockPos, CameraFeed> FEEDS = new LinkedHashMap<>();

    private static boolean renderingFeed;
    private static Framebuffer renderTarget;
    private static boolean disabled;
    private static int failureCount;

    private static final class CameraFeed {
        final BlockPos cameraPos;
        final Identifier textureId;
        final SimpleFramebuffer framebuffer;
        Direction facing;
        long lastRender;

        CameraFeed(MinecraftClient client, BlockPos cameraPos, Direction facing) {
            this.cameraPos = cameraPos.toImmutable();
            this.facing = facing;
            this.framebuffer = new SimpleFramebuffer(
                    WIDTH, HEIGHT, true, MinecraftClient.IS_SYSTEM_MAC);
            this.framebuffer.setTexFilter(GL11.GL_LINEAR);
            this.textureId = Identifier.of(ICyouMod.MOD_ID,
                    "camera_feed_" + Long.toUnsignedString(cameraPos.asLong()));
            client.getTextureManager().registerTexture(textureId,
                    new FramebufferTexture(framebuffer.getColorAttachment()));
        }

        void close(MinecraftClient client) {
            client.getTextureManager().destroyTexture(textureId);
            framebuffer.delete();
        }
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
        Map<BlockPos, Direction> active = collectActiveCameras(client);
        reconcileFeeds(client, active);
        if (active.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        CameraFeed next = active.entrySet().stream()
                .map(entry -> FEEDS.computeIfAbsent(entry.getKey(), key ->
                        new CameraFeed(client, key, entry.getValue())))
                .peek(feed -> feed.facing = active.get(feed.cameraPos))
                .filter(feed -> now - feed.lastRender >= FRAME_INTERVAL_MS)
                .min(Comparator.comparingLong(feed -> feed.lastRender))
                .orElse(null);
        if (next != null) {
            renderCamera(client, gameRenderer, tickCounter, next, now);
        }
    }

    private static Map<BlockPos, Direction> collectActiveCameras(MinecraftClient client) {
        Map<BlockPos, Direction> active = new LinkedHashMap<>();
        double maxDistanceSq = SCREEN_VIEW_DISTANCE * SCREEN_VIEW_DISTANCE;
        for (ScreenBlockEntity screen : SCREENS.values()) {
            BlockPos camPos = screen.getLastCamPos();
            if (camPos == null || screen.getWorld() != client.world
                    || client.player.squaredDistanceTo(Vec3d.ofCenter(screen.getPos()))
                    > maxDistanceSq) {
                continue;
            }
            active.put(camPos.toImmutable(), Direction.byId(screen.getLastFacingId()));
        }
        return active;
    }

    private static void reconcileFeeds(MinecraftClient client,
                                       Map<BlockPos, Direction> active) {
        Set<BlockPos> retained = new HashSet<>(active.keySet());
        Iterator<Map.Entry<BlockPos, CameraFeed>> iterator = FEEDS.entrySet().iterator();
        while (iterator.hasNext()) {
            CameraFeed feed = iterator.next().getValue();
            if (!retained.contains(feed.cameraPos)) {
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

        renderTarget = feed.framebuffer;
        renderingFeed = true;
        try {
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
            renderTarget = null;
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
        return renderingFeed ? renderTarget : null;
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
        BlockPos camPos = screen.getLastCamPos();
        return camPos == null ? null : FEEDS.get(camPos);
    }
}
