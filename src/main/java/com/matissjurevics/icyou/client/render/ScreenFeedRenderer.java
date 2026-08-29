package com.matissjurevics.icyou.client.render;

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
 * a bouncing idle logo or live camera footage with status overlays.
 */
public class ScreenFeedRenderer implements BlockEntityRenderer<ScreenBlockEntity> {

    private static final DirectionProperty FACING_PROP = ScreenBlock.FACING;
    private static final int FULL_BRIGHT = 0xF000F0;

    // The model's glass face is at local z=12.65/16 (0.290625 from centre).
    // Give the dynamic surface a visible depth gap; a near-coplanar quad loses
    // the depth test to the baked display face, especially at oblique angles.
    private static final float FACE_OFFSET = 0.27f;
    private static final float IDLE_FACE_OFFSET = 0.24f;
    private static final float LOGO_FACE_OFFSET = 0.235f;
    private static final String[] IDLE_LOGO = {
            "1110111010101110101",
            "0100100010101010101",
            "0100100001001010101",
            "0100100001001010101",
            "1110111001001110111"
    };
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
            float textScale = 0.0105f * Math.min(width, height);
            String camDir = Direction.byId(blockEntity.getLastFacingId()).asString();
            String channel = blockEntity.getLastCount() > 1
                    ? blockEntity.getLastIndex() + "/" + blockEntity.getLastCount()
                    : "";
            drawText(matrices, vertexConsumers, FULL_BRIGHT,
                    Text.literal("CAM " + channel + " [" + camDir + "]").formatted(Formatting.GREEN),
                    halfWidth * 0.55f, halfHeight * 0.12f, textScale, 0xFF60FF60);

            // Blinking LIVE marker is only shown over actual camera footage.
            long frame = StylizedFeed.INSTANCE.frame();
            if (frame % 20 < 14) {
                drawText(matrices, vertexConsumers, FULL_BRIGHT,
                        Text.literal("LIVE"), -0.015f * width, -halfHeight * 0.23f,
                        textScale,
                        frame % 40 < 20 ? 0xFFFF3030 : 0xFF902020);
            }
        } else {
            drawIdleScreensaver(matrices, vertexConsumers, halfWidth, halfHeight,
                    width, height);
        }

        matrices.pop();
    }

    /** Draws a DVD-style ICyou logo screensaver over a solid black panel. */
    private void drawIdleScreensaver(MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers,
                                     float halfWidth, float halfHeight,
                                     int width, int height) {
        quad(matrices, vertexConsumers,
                -halfWidth, -halfHeight, halfWidth, halfHeight,
                IDLE_FACE_OFFSET, 0, 0, 0);

        float logoWidth = 0.68f * Math.min(width, height);
        float pixel = logoWidth / IDLE_LOGO[0].length();
        float logoHeight = pixel * IDLE_LOGO.length;
        float logoHalfWidth = logoWidth / 2f;
        float logoHalfHeight = logoHeight / 2f;
        float margin = 0.04f * Math.min(width, height);
        long frame = StylizedFeed.INSTANCE.frame();

        float x = bounce(frame * 0.0065f,
                -halfWidth + logoHalfWidth + margin,
                halfWidth - logoHalfWidth - margin);
        float y = bounce(frame * 0.0045f + 0.37f,
                -halfHeight + logoHalfHeight + margin,
                halfHeight - logoHalfHeight - margin);
        drawPixelLogo(matrices, vertexConsumers, x, y, pixel,
                logoHalfWidth, logoHalfHeight);
    }

    private void drawPixelLogo(MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers,
                               float centerX, float centerY, float pixel,
                               float halfWidth, float halfHeight) {
        float left = centerX - halfWidth;
        float bottom = centerY - halfHeight;
        for (int row = 0; row < IDLE_LOGO.length; row++) {
            String pixels = IDLE_LOGO[IDLE_LOGO.length - 1 - row];
            for (int column = 0; column < pixels.length(); column++) {
                if (pixels.charAt(column) != '1') {
                    continue;
                }
                // Local screen X is mirrored when viewed from the display face.
                float x1 = left + (pixels.length() - 1 - column) * pixel;
                float y1 = bottom + row * pixel;
                quad(matrices, vertexConsumers, x1, y1,
                        x1 + pixel, y1 + pixel,
                        LOGO_FACE_OFFSET, 96, 255, 96);
            }
        }
    }

    /** Triangle wave constrained to [min, max], producing a clean edge bounce. */
    private static float bounce(float distance, float min, float max) {
        float range = max - min;
        if (range <= 0f) {
            return (min + max) / 2f;
        }
        float phase = distance % (range * 2f);
        return phase <= range ? min + phase : max - (phase - range);
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
                             float x1, float y1, float x2, float y2, float z,
                             int r, int g, int b) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // getDebugFilledBox uses a triangle strip: left-bottom, left-top,
        // right-bottom, right-top. Standard quad ordering leaves one diagonal
        // half culled, which caused the old triangular sparkle artifacts.
        buffer.vertex(matrix, x1, y1, z).color(r, g, b, 255);
        buffer.vertex(matrix, x1, y2, z).color(r, g, b, 255);
        buffer.vertex(matrix, x2, y1, z).color(r, g, b, 255);
        buffer.vertex(matrix, x2, y2, z).color(r, g, b, 255);
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

}
