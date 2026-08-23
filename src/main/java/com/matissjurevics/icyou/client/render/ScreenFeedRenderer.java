package com.matissjurevics.icyou.client.render;

import com.matissjurevics.icyou.feed.FeedBlip;
import com.matissjurevics.icyou.feed.StylizedFeed;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;

/**
 * Renders the stylized CCTV feed directly onto a screen block's display:
 * animated static, camera status text, and a blinking LIVE indicator.
 *
 * <p>Phase 3 replaces this with entity blips (networked) and eventually a
 * render-to-texture true footage feed; the drawing surface stays the same.</p>
 */
public class ScreenFeedRenderer implements BlockEntityRenderer<ScreenBlockEntity> {

    private static final DirectionProperty FACING_PROP = net.minecraft.block.FacingBlock.FACING;

    private static final float PANEL_HALF = 0.3125f;   // 5/16 — half of the 10px panel
    private static final float FACE_OFFSET = -0.1898f; // just proud of the display plane
    private static final int GRID = 10;                // static cells per row/column

    private final TextRenderer textRenderer;

    public ScreenFeedRenderer(BlockEntityRendererFactory.Context context) {
        this.textRenderer = context.getTextRenderer();
    }

    @Override
    public void render(ScreenBlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        boolean hasSignal = blockEntity.isReceiving();

        matrices.push();
        // Move to block centre and rotate so -Z is the direction the screen faces.
        matrices.translate(0.5, 0.5, 0.5);
        Direction facing = Direction.NORTH;
        if (blockEntity.getCachedState().contains(FACING_PROP)) {
            facing = blockEntity.getCachedState().get(FACING_PROP);
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees(facing)));

        drawStatic(matrices, vertexConsumers);
        drawBlips(matrices, vertexConsumers, blockEntity);

        if (hasSignal) {
            String camDir = Direction.byId(blockEntity.getLastFacingId()).asString();
            String channel = blockEntity.getLastCount() > 1
                    ? blockEntity.getLastIndex() + "/" + blockEntity.getLastCount()
                    : "";
            drawText(matrices, vertexConsumers, light,
                    Text.literal("CAM " + channel + " [" + camDir + "]").formatted(Formatting.GREEN),
                    PANEL_HALF * 0.55f, 0.06f, 0xFF60FF60);
        } else {
            drawText(matrices, vertexConsumers, light,
                    Text.literal("NO SIGNAL"), -0.09f, 0.06f, 0xFFFF5050);
        }

        // Blinking LIVE marker.
        long frame = StylizedFeed.INSTANCE.frame();
        if (frame % 20 < 14) {
            drawText(matrices, vertexConsumers, light,
                    Text.literal("LIVE"), -0.015f, -0.115f,
                    frame % 40 < 20 ? 0xFFFF3030 : 0xFF902020);
        }

        matrices.pop();
    }

    /** Fills the panel with per-cell pseudo-random static, tinted like night vision. */
    private void drawStatic(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        long seedBase = StylizedFeed.INSTANCE.frame();
        float cell = (PANEL_HALF * 2f) / GRID;

        for (int gy = 0; gy < GRID; gy++) {
            for (int gx = 0; gx < GRID; gx++) {
                int h = hash(gx, gy, (int) (seedBase & 0xFFFF));
                int v = 30 + (h & 0x2F);                       // mostly dark greys
                int r = v, g = v + ((h >>> 6) & 0x18), b = v + ((h >>> 8) & 0x20);
                if ((h & 0x3F) == 0) {                         // occasional bright cell
                    r = 140; g = 220; b = 255;
                }
                float x1 = -PANEL_HALF + gx * cell;
                float y1 = -PANEL_HALF + gy * cell;
                quad(matrices, vertexConsumers, x1, y1, x1 + cell, y1 + cell, r, g, b);
            }
        }
    }

    /** Draws networked entity blips on top of the static. */
    private void drawBlips(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                           ScreenBlockEntity blockEntity) {
        for (FeedBlip blip : blockEntity.getClientBlips()) {
            float px = -PANEL_HALF + blip.u() * PANEL_HALF * 2f;
            float py = PANEL_HALF - blip.v() * PANEL_HALF * 2f;
            float s = 0.028f;
            switch (blip.kind()) {
                case FeedBlip.KIND_PLAYER -> quad(matrices, vertexConsumers,
                        px - s, py - s, px + s, py + s, 60, 255, 120);
                case FeedBlip.KIND_MONSTER -> quad(matrices, vertexConsumers,
                        px - s, py - s, px + s, py + s, 255, 70, 50);
                default -> quad(matrices, vertexConsumers,
                        px - s, py - s, px + s, py + s, 255, 220, 80);
            }
        }
    }

    private void drawText(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                          int light, Text text, float x, float y, int argb) {
        matrices.push();
        float scale = 0.0105f;
        matrices.translate(x, y, FACE_OFFSET - 0.002f);
        matrices.scale(scale, scale, scale);
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        textRenderer.draw(text, -textRenderer.getWidth(text) / 2f, -4f, argb,
                false, positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        matrices.pop();
    }

    private static void quad(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                             float x1, float y1, float x2, float y2, int r, int g, int b) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float z = FACE_OFFSET;
        buffer.vertex(matrix, x1, y1, z).color(r, g, b, 255);
        buffer.vertex(matrix, x2, y1, z).color(r, g, b, 255);
        buffer.vertex(matrix, x2, y2, z).color(r, g, b, 255);
        buffer.vertex(matrix, x1, y2, z).color(r, g, b, 255);
    }

    private static float yawDegrees(Direction facing) {
        return switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    /** Deterministic integer hash so static is stable per cell per frame. */
    private static int hash(int x, int y, int frame) {
        long h = x * 374761393L + y * 668265263L + frame * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return (int) (h ^ (h >>> 16));
    }
}
