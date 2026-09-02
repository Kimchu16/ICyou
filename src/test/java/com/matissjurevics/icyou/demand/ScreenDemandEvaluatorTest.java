package com.matissjurevics.icyou.demand;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class ScreenDemandEvaluatorTest {

    private static final RegistryKey<World> OVERWORLD = World.OVERWORLD;
    private static final RegistryKey<World> OTHER = RegistryKey.of(RegistryKeys.WORLD,
            Identifier.of("icyou", "other"));

    @Test
    void genuinePlayerAtSixtyFourBlocksCreatesDemand() {
        var screen = screen(true);
        var player = player(OVERWORLD, 64.5, 0.5, 0.5, false);

        assertTrue(ScreenDemandEvaluator.hasDemand(screen, List.of(player)));
    }

    @Test
    void playerBeyondRangeOrInAnotherDimensionDoesNotCreateDemand() {
        var screen = screen(true);

        assertFalse(ScreenDemandEvaluator.hasDemand(screen,
                List.of(player(OVERWORLD, 64.51, 0.5, 0.5, false))));
        assertFalse(ScreenDemandEvaluator.hasDemand(screen,
                List.of(player(OTHER, 0.5, 0.5, 0.5, false))));
    }

    @Test
    void unloadedScreenChunkDoesNotCreateDemand() {
        assertFalse(ScreenDemandEvaluator.hasDemand(screen(false),
                List.of(player(OVERWORLD, 0.5, 0.5, 0.5, false))));
    }

    @Test
    void renderAgentNeverCreatesScreenDemand() {
        assertFalse(ScreenDemandEvaluator.hasDemand(screen(true),
                List.of(player(OVERWORLD, 0.5, 0.5, 0.5, true))));
    }

    private static ScreenDemandEvaluator.ScreenView screen(boolean loaded) {
        return new ScreenDemandEvaluator.ScreenView(UUID.randomUUID(), OVERWORLD,
                BlockPos.ORIGIN, loaded);
    }

    private static ScreenDemandEvaluator.PlayerView player(RegistryKey<World> dimension,
                                                            double x, double y, double z,
                                                            boolean renderAgent) {
        return new ScreenDemandEvaluator.PlayerView(dimension, x, y, z, renderAgent);
    }
}
