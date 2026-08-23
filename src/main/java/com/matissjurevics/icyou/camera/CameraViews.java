package com.matissjurevics.icyou.camera;

import java.util.List;

import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Server-side description of what a camera "sees" — the data source for the
 * {@code icyou:stylized} feed. Real render-to-texture feeds will replace the
 * rendering side, not this.
 */
public final class CameraViews {

    private CameraViews() {}

    /** How far a camera can see, in blocks. */
    public static final double RANGE = 16.0;
    /** Total horizontal field of view, in degrees. */
    public static final double FOV_DEGREES = 90.0;

    /**
     * Builds a one-line live status for a camera: its direction and which
     * entities are inside its view cone.
     */
    public static Text describe(World world, BlockPos camPos, int index) {
        if (!(world.getBlockState(camPos).getBlock() instanceof CameraBlock camera)) {
            return Text.literal(String.format("CAM-%d: signal lost", index));
        }

        Direction facing = world.getBlockState(camPos).get(CameraBlock.FACING);
        Vec3d origin = Vec3d.ofCenter(camPos);

        List<LivingEntity> spotted = world.getEntitiesByClass(LivingEntity.class,
                new Box(camPos).expand(RANGE),
                entity -> isInView(origin, facing, entity.getBoundingBox().getCenter()));

        String summary;
        if (spotted.isEmpty()) {
            summary = "clear";
        } else {
            String names = spotted.stream().limit(3)
                    .map(entity -> entity.getName().getString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (spotted.size() > 3) {
                names += " +" + (spotted.size() - 3);
            }
            summary = spotted.size() + " entity" + (spotted.size() == 1 ? "" : "s")
                    + ": " + names;
        }

        return Text.literal(String.format("CAM-%d [%s]: %s",
                index, facing.asString(), summary));
    }

    private static boolean isInView(Vec3d origin, Direction facing, Vec3d target) {
        Vec3d relative = target.subtract(origin);
        if (relative.lengthSquared() > RANGE * RANGE) {
            return false;
        }
        if (relative.lengthSquared() < 0.01) {
            return true; // entity is inside the camera block itself
        }
        Vec3d forward = new Vec3d(
                facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ());
        double minDot = Math.cos(Math.toRadians(FOV_DEGREES / 2.0));
        return relative.normalize().dotProduct(forward) >= minDot;
    }
}
