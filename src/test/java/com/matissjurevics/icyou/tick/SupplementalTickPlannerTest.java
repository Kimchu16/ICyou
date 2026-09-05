package com.matissjurevics.icyou.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.lease.ChunkLeaseManager;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

class SupplementalTickPlannerTest {

    private static final RegistryKey<World> OVERWORLD = World.OVERWORLD;
    private static final RegistryKey<World> OTHER = RegistryKey.of(RegistryKeys.WORLD,
            Identifier.of("icyou", "other"));

    @Test
    void selectsLoadedLeasesVanillaDidNotTick() {
        ChunkPos missing = new ChunkPos(4, 5);
        var leases = Set.of(location(OVERWORLD, missing),
                location(OVERWORLD, new ChunkPos(6, 7)));

        var selected = SupplementalTickPlanner.select(leases, OVERWORLD,
                Set.of(new ChunkPos(6, 7).toLong()), ignored -> true);

        assertEquals(Set.of(missing), selected);
    }

    @Test
    void neverDuplicatesVanillaTick() {
        ChunkPos chunk = new ChunkPos(4, 5);

        var selected = SupplementalTickPlanner.select(Set.of(location(OVERWORLD, chunk)),
                OVERWORLD, Set.of(chunk.toLong()), ignored -> true);

        assertTrue(selected.isEmpty());
    }

    @Test
    void ignoresOtherDimensionsAndChunksStillLoading() {
        ChunkPos chunk = new ChunkPos(4, 5);
        var leases = Set.of(location(OTHER, chunk),
                location(OVERWORLD, new ChunkPos(8, 9)));

        var selected = SupplementalTickPlanner.select(leases, OVERWORLD, Set.of(),
                ignored -> false);

        assertTrue(selected.isEmpty());
    }

    @Test
    void disabledForFrozenOrZeroSpeedWorlds() {
        assertTrue(SupplementalTickPlanner.enabled(true, 3));
        assertFalse(SupplementalTickPlanner.enabled(false, 3));
        assertFalse(SupplementalTickPlanner.enabled(true, 0));
    }

    private static ChunkLeaseManager.LeaseLocation location(RegistryKey<World> dimension,
                                                            ChunkPos chunk) {
        return new ChunkLeaseManager.LeaseLocation(dimension, chunk);
    }
}
