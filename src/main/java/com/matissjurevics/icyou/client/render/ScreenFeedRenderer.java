package com.matissjurevics.icyou.client.render;

import com.matissjurevics.icyou.feed.FeedBlip;
import com.matissjurevics.icyou.client.render.RttFeedManager;
import com.matissjurevics.icyou.feed.StylizedFeed;
import com.matissjurevics.icyou.screen.ScreenBlock;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;

import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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

    private static final DirectionProperty FACING_PROP = ScreenBlock.FACING;
    private static final int FULL_BRIGHT = 0xF000F0;

    // The model's glass face is at local z=12.65/16 (0.290625 from centre).
    // Give the dynamic surface a visible depth gap; a near-coplanar quad loses
    // the depth test to the baked blue face, especially at oblique angles.
    private static final float FACE_OFFSET = 0.27f;
    private static final int GRID_PER_BLOCK = 10;

    private final TextRenderer textRenderer;

    public ScreenFeedRenderer(BlockEntityRendererFactory.Context context) {
        this.textRenderer = context.getTextRenderer();
    }

    @Override
    public void render(ScreenBlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // A camera pass can see screens. Never sample an FBO while rendering to it.
        if (RttFeedManager.isRenderingFeed()) {
            return;
        }
        boolean hasSignal = blockEntity.isReceiving();

        BlockState state = blockEntity.getCachedState();
        Direction facing = Direction.NORTH;
        if (state.contains(FACING_PROP)) {
            facing = state.get(FACING_PROP);
        }
        ScreenBlock screenBlock = state.getBlock() instanceof ScreenBlock block ? block : null;
        int width = screenBlock != null ? screenBlock.getDisplayWidth() : 1;
        int height = screenBlock != null ? screenBlock.getDisplayHeight() : 1;
        float halfWidth = width / 2f;
        float halfHeight = height / 2f;
        Direction right = facing.rotateYCounterclockwise();

        matrices.push();
        // The block entity lives in the bottom-left part. Move to the centre of
        // the complete display before rotating and drawing one continuous feed.
        matrices.translate(
                0.5 + right.getOffsetX() * (width - 1) / 2.0,
                0.5 + (height - 1) / 2.0,
                0.5 + right.getOffsetZ() * (width - 1) / 2.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees(facing)));

        boolean rtt = RttFeedManager.hasLiveFeed(blockEntity);

        if (rtt) {
            // Live camera POV: draw the RTT framebuffer texture over the panel.
            Identifier texId = RttFeedManager.textureIdFor(blockEntity);
            if (texId != null) {
                VertexConsumer vc = vertexConsumers.getBuffer(
                        RenderLayer.getEntityTranslucent(texId));
                Matrix4f m4 = matrices.peek().getPositionMatrix();
                float z = FACE_OFFSET;
                // The display is viewed from local -Z. Keep the winding and
                // normal pointed that way so the render layer does not cull it.
                // The feed FBO texture is stored bottom-up (GL framebuffer origin) and
                // this screen face mirrors X, so sample it with both U and V flipped
                // or the image appears upside down and/or mirrored.
                vc.vertex(m4, -halfWidth,  halfHeight, z).color(255, 255, 255, 255)
                        .texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
                        .normal(0, 0, -1);
                vc.vertex(m4,  halfWidth,  halfHeight, z).color(255, 255, 255, 255)
                        .texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
                        .normal(0, 0, -1);
                vc.vertex(m4,  halfWidth, -halfHeight, z).color(255, 255, 255, 255)
                        .texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
                        .normal(0, 0, -1);
                vc.vertex(m4, -halfWidth, -halfHeight, z).color(255, 255, 255, 255)
                        .texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
                        .normal(0, 0, -1);
            }
        } else {
            drawStatic(matrices, vertexConsumers, halfWidth, halfHeight, width, height);
            drawBlips(matrices, vertexConsumers, blockEntity, halfWidth, halfHeight);
        }

        float textScale = 0.0105f * Math.min(width, height);
        if (hasSignal) {
            String camDir = Direction.byId(blockEntity.getLastFacingId()).asString();
            String channel = blockEntity.getLastCount() > 1
                    ? blockEntity.getLastIndex() + "/" + blockEntity.getLastCount()
                    : "";
            drawText(matrices, vertexConsumers, FULL_BRIGHT,
                    Text.literal("CAM " + channel + " [" + camDir + "]").formatted(Formatting.GREEN),
                    halfWidth * 0.55f, halfHeight * 0.12f, textScale, 0xFF60FF60);
        } else {
            drawText(matrices, vertexConsumers, FULL_BRIGHT,
                    Text.literal("NO SIGNAL"), -0.09f * width, halfHeight * 0.12f,
                    textScale, 0xFFFF5050);
        }

        // Blinking LIVE marker.
        long frame = StylizedFeed.INSTANCE.frame();
        if (frame % 20 < 14) {
            drawText(matrices, vertexConsumers, FULL_BRIGHT,
                    Text.literal("LIVE"), -0.015f * width, -halfHeight * 0.23f,
                    textScale,
                    frame % 40 < 20 ? 0xFFFF3030 : 0xFF902020);
        }

        matrices.pop();
    }

    /** Fills the panel with neutral monochrome static. */
    private void drawStatic(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                            float halfWidth, float halfHeight, int width, int height) {
        long seedBase = StylizedFeed.INSTANCE.frame();
        int gridWidth = GRID_PER_BLOCK * width;
        int gridHeight = GRID_PER_BLOCK * height;
        float cellWidth = (halfWidth * 2f) / gridWidth;
        float cellHeight = (halfHeight * 2f) / gridHeight;

        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                int h = hash(gx, gy, (int) (seedBase & 0xFFFF));
                int v = 24 + (h & 0x1F); // restrained dark greys; no blue sparkle cells
                float x1 = -halfWidth + gx * cellWidth;
                float y1 = -halfHeight + gy * cellHeight;
                quad(matrices, vertexConsumers, x1, y1,
                        x1 + cellWidth, y1 + cellHeight, v, v, v);
            }
        }
    }

    /** Draws networked entity blips on top of the static. */
    private void drawBlips(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                           ScreenBlockEntity blockEntity, float halfWidth, float halfHeight) {
        for (FeedBlip blip : blockEntity.getClientBlips()) {
            float px = -halfWidth + blip.u() * halfWidth * 2f;
            float py = halfHeight - blip.v() * halfHeight * 2f;
            float s = 0.028f * Math.min(halfWidth * 2f, halfHeight * 2f);
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
                          int light, Text text, float x, float y, float scale, int argb) {
        matrices.push();
        matrices.translate(x, y, FACE_OFFSET - 0.001f);
        // This face exposes TextRenderer's back, so mirror only its X axis.
        // A 180-degree Y rotation also reverses its depth-facing side and can
        // make the labels disappear behind the panel.
        matrices.scale(-scale, scale, scale);
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        textRenderer.draw(text, -textRenderer.getWidth(text) / 2f, -4f, argb,
                false, positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(ScreenBlockEntity blockEntity) {
        return blockEntity.getCachedState().getBlock() instanceof ScreenBlock block
                && (block.getDisplayWidth() > 1 || block.getDisplayHeight() > 1);
    }

    private static void quad(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                             float x1, float y1, float x2, float y2, int r, int g, int b) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float z = FACE_OFFSET;
        buffer.vertex(matrix, x1, y1, z).color(r, g, b, 255);
        buffer.vertex(matrix, x1, y2, z).color(r, g, b, 255);
        buffer.vertex(matrix, x2, y2, z).color(r, g, b, 255);
        buffer.vertex(matrix, x2, y1, z).color(r, g, b, 255);
    }

    private static float yawDegrees(Direction facing) {
        return switch (facing) {
            // Matrix-stack Y rotation is counter-clockwise, while block-model
            // JSON's positive Y variants rotate clockwise when viewed above.
            case EAST -> -90f;
            case SOUTH -> 180f;
            case WEST -> 90f;
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
