package com.matissjurevics.icyou.tick;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongPredicate;

import com.matissjurevics.icyou.lease.ChunkLeaseManager;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/** Selects leased chunks that vanilla did not random-tick during this world tick. */
public final class SupplementalTickPlanner {

    private SupplementalTickPlanner() {
    }

    public static boolean enabled(boolean worldTicking, int randomTickSpeed) {
        return worldTicking && randomTickSpeed > 0;
    }

    public static Set<ChunkPos> select(Set<ChunkLeaseManager.LeaseLocation> leases,
                                       RegistryKey<World> dimension,
                                       Set<Long> vanillaTicked,
                                       LongPredicate loaded) {
        Objects.requireNonNull(leases, "leases");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(vanillaTicked, "vanillaTicked");
        Objects.requireNonNull(loaded, "loaded");
        Set<ChunkPos> selected = new LinkedHashSet<>();
        for (ChunkLeaseManager.LeaseLocation lease : leases) {
            long packed = lease.chunk().toLong();
            if (lease.dimension().equals(dimension)
                    && !vanillaTicked.contains(packed)
                    && loaded.test(packed)) {
                selected.add(lease.chunk());
            }
        }
        return Set.copyOf(selected);
    }
}
