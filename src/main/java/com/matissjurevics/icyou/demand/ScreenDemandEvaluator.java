package com.matissjurevics.icyou.demand;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Pure rules for deciding whether a loaded in-world screen creates demand. */
public final class ScreenDemandEvaluator {

    public static final double MAX_DISTANCE =
            CameraOverhaulContracts.SCREEN_DEMAND_RANGE_BLOCKS;
    private static final double MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;

    public record ScreenView(UUID cameraId, RegistryKey<World> dimension,
                             BlockPos position, boolean chunkLoaded) {
        public ScreenView {
            Objects.requireNonNull(cameraId, "cameraId");
            Objects.requireNonNull(dimension, "dimension");
            position = Objects.requireNonNull(position, "position").toImmutable();
        }
    }

    public record PlayerView(RegistryKey<World> dimension, double x, double y, double z,
                             boolean renderAgent) {
        public PlayerView {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    private ScreenDemandEvaluator() {
    }

    public static boolean hasDemand(ScreenView screen, Collection<PlayerView> players) {
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(players, "players");
        if (!screen.chunkLoaded()) {
            return false;
        }
        double centerX = screen.position().getX() + 0.5;
        double centerY = screen.position().getY() + 0.5;
        double centerZ = screen.position().getZ() + 0.5;
        return players.stream().anyMatch(player -> !player.renderAgent()
                && player.dimension().equals(screen.dimension())
                && squaredDistance(player, centerX, centerY, centerZ)
                        <= MAX_DISTANCE_SQUARED);
    }

    private static double squaredDistance(PlayerView player, double x, double y, double z) {
        double dx = player.x() - x;
        double dy = player.y() - y;
        double dz = player.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
