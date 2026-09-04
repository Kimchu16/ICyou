package com.matissjurevics.icyou.client.agent;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.client.render.OffscreenRenderContext;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/** Fairly renders one due remote camera world into a bounded RGBA frame. */
public final class RemoteOffscreenRenderer {

    private static final float FOV_DEGREES = 70.0f;
    private static final float FAR_PLANE = 64.0f;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final int MAX_TERRAIN_WARMUP_ATTEMPTS =
            CameraOverhaulContracts.VIDEO_FPS * 30;
    private static final long FRAME_INTERVAL_NANOS =
            1_000_000_000L / CameraOverhaulContracts.VIDEO_FPS;
    private static final int FRAME_BYTES = Math.multiplyExact(Math.multiplyExact(
            CameraOverhaulContracts.VIDEO_WIDTH,
            CameraOverhaulContracts.VIDEO_HEIGHT), 4);

    private static final class Target implements AutoCloseable {
        private final long snapshotSequence;
        private final SimpleFramebuffer framebuffer;
        private final ByteBuffer readback = ByteBuffer.allocateDirect(FRAME_BYTES);
        private long lastAttemptNanos = Long.MIN_VALUE;
        private long nextFrameSequence;
        private int failures;
        private int terrainWarmupAttempts;

        private Target(long snapshotSequence) {
            this.snapshotSequence = snapshotSequence;
            framebuffer = new SimpleFramebuffer(CameraOverhaulContracts.VIDEO_WIDTH,
                    CameraOverhaulContracts.VIDEO_HEIGHT, true,
                    MinecraftClient.IS_SYSTEM_MAC);
            framebuffer.setTexFilter(GL11.GL_LINEAR);
        }

        @Override
        public void close() {
            framebuffer.delete();
        }
    }

    private static final class FeedCamera extends Camera {
        private void configure(ClientWorld world, Entity focus, Vec3d position,
                               float yaw, float pitch, float tickDelta) {
            update(world, focus, true, false, tickDelta);
            setPos(position);
            setRotation(yaw, pitch);
        }
    }

    private static final Map<UUID, Target> TARGETS = new LinkedHashMap<>();

    private RemoteOffscreenRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> reconcile());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void renderFrame(MinecraftClient client, GameRenderer gameRenderer,
                                   RenderTickCounter tickCounter) {
        if (client.world == null || client.player == null
                || OffscreenRenderContext.active()) {
            return;
        }
        Map<UUID, RemoteSceneWorld> worlds = ClientRemoteSceneLifecycle.worlds();
        Map<UUID, JobAssignment> jobs = ClientRenderAgentLifecycle.agent().activeJobs();
        reconcile(worlds, jobs.keySet());
        for (Map.Entry<UUID, RemoteSceneWorld> entry : worlds.entrySet()) {
            if (jobs.containsKey(entry.getKey())) {
                try {
                    TARGETS.computeIfAbsent(entry.getKey(), ignored ->
                            new Target(entry.getValue().snapshotSequence()));
                } catch (Throwable error) {
                    ICyouMod.LOGGER.error("Remote camera target creation failed for job {}",
                            entry.getKey(), error);
                    close(entry.getKey());
                    ClientRenderAgentLifecycle.agent().markFailed(entry.getKey(),
                            "offscreen camera target creation failed");
                }
            }
        }

        long nowNanos = System.nanoTime();
        UUID selected = RemoteRenderCadence.select(TARGETS.entrySet().stream()
                .map(entry -> new RemoteRenderCadence.Candidate(entry.getKey(),
                        entry.getValue().lastAttemptNanos)).toList(),
                nowNanos, FRAME_INTERVAL_NANOS).orElse(null);
        if (selected == null) {
            return;
        }
        Target target = TARGETS.get(selected);
        target.lastAttemptNanos = nowNanos;
        try {
            renderOne(client, gameRenderer, tickCounter, selected, jobs.get(selected),
                    worlds.get(selected), target);
        } catch (Throwable error) {
            ICyouMod.LOGGER.error("Remote camera cleanup failed for job {}", selected, error);
            close(selected);
            ClientRenderAgentLifecycle.agent().markFailed(selected,
                    "offscreen camera cleanup failed");
        }
    }

    private static void renderOne(MinecraftClient client, GameRenderer gameRenderer,
            RenderTickCounter tickCounter, UUID jobId, JobAssignment assignment,
            RemoteSceneWorld remote, Target target) {
        if (assignment == null || remote == null) {
            return;
        }
        ClientWorld previousWorld = client.world;
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        float tickDelta = tickCounter.getTickDelta(true);
        Direction facing = facing(remote.world(), assignment.camera());
        Vec3d position = cameraPosition(assignment.camera().position(), facing);
        FeedCamera camera = new FeedCamera();
        camera.configure(remote.world(), client.player, position, yawFor(facing),
                pitchFor(facing), tickDelta);
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(FOV_DEGREES),
                (float) CameraOverhaulContracts.VIDEO_WIDTH
                        / CameraOverhaulContracts.VIDEO_HEIGHT,
                0.05f, FAR_PLANE);
        Matrix4f view = new Matrix4f().rotation(
                camera.getRotation().conjugate(new Quaternionf()));

        boolean terminalFailure = false;
        try {
            client.world = remote.world();
            try (OffscreenRenderContext.Scope ignored =
                         OffscreenRenderContext.enter(target.framebuffer)) {
                target.framebuffer.setClearColor(0.01f, 0.015f, 0.02f, 1.0f);
                target.framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
                target.framebuffer.beginWrite(true);
                gameRenderer.loadProjectionMatrix(projection);
                remote.renderer().setupFrustum(position, view, projection);
                remote.renderer().render(tickCounter, false, camera, gameRenderer,
                        gameRenderer.getLightmapTextureManager(), view, projection);
                if (remote.renderer().isTerrainRenderComplete()) {
                    capture(jobId, assignment, target);
                } else if (++target.terrainWarmupAttempts
                        >= MAX_TERRAIN_WARMUP_ATTEMPTS) {
                    throw new IllegalStateException("Remote terrain did not become ready");
                }
            }
            target.failures = 0;
        } catch (Throwable error) {
            target.failures++;
            ICyouMod.LOGGER.error("Remote camera render failed ({}/{}) for job {}",
                    target.failures, MAX_CONSECUTIVE_FAILURES, jobId, error);
            if (target.failures >= MAX_CONSECUTIVE_FAILURES) {
                terminalFailure = true;
            }
        } finally {
            client.world = previousWorld;
            try {
                target.framebuffer.endWrite();
            } finally {
                try {
                    client.getFramebuffer().beginWrite(true);
                } finally {
                    gameRenderer.loadProjectionMatrix(previousProjection);
                }
            }
        }
        if (terminalFailure) {
            close(jobId);
            ClientRenderAgentLifecycle.agent().markFailed(jobId,
                    "offscreen camera rendering failed");
        }
    }

    private static void capture(UUID jobId, JobAssignment assignment, Target target) {
        target.readback.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, CameraOverhaulContracts.VIDEO_WIDTH,
                CameraOverhaulContracts.VIDEO_HEIGHT, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, target.readback);
        RemoteFrameStore.put(new RemoteVideoFrame(jobId, assignment.revision(),
                assignment.camera().deviceId(), target.nextFrameSequence++,
                System.currentTimeMillis(), RgbaFrameCapture.topDown(target.readback,
                        CameraOverhaulContracts.VIDEO_WIDTH,
                        CameraOverhaulContracts.VIDEO_HEIGHT)));
    }

    private static Direction facing(ClientWorld world, CameraRef camera) {
        BlockState state = world.getBlockState(camera.position());
        return state.contains(CameraBlock.FACING)
                ? state.get(CameraBlock.FACING) : Direction.NORTH;
    }

    private static Vec3d cameraPosition(BlockPos position, Direction facing) {
        return Vec3d.ofCenter(position).add(Vec3d.of(facing.getVector()).multiply(0.65))
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

    private static void reconcile() {
        reconcile(ClientRemoteSceneLifecycle.worlds(),
                ClientRenderAgentLifecycle.agent().activeJobs().keySet());
    }

    private static void reconcile(Map<UUID, RemoteSceneWorld> worlds,
                                  Set<UUID> activeJobs) {
        TARGETS.keySet().stream().filter(jobId -> !activeJobs.contains(jobId)
                        || !worlds.containsKey(jobId)
                        || TARGETS.get(jobId).snapshotSequence
                        != worlds.get(jobId).snapshotSequence())
                .toList().forEach(RemoteOffscreenRenderer::close);
        RemoteFrameStore.retain(activeJobs);
    }

    private static void close(UUID jobId) {
        Target target = TARGETS.remove(jobId);
        if (target != null) {
            target.close();
        }
        RemoteFrameStore.remove(jobId);
    }

    private static void clear() {
        TARGETS.values().forEach(Target::close);
        TARGETS.clear();
        RemoteFrameStore.clear();
    }
}
