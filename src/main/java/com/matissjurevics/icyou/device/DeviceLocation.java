package com.matissjurevics.icyou.device;

import java.util.Objects;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Dimension-aware lookup key for a device's current block location. */
public record DeviceLocation(RegistryKey<World> dimension, BlockPos position) {

    public DeviceLocation {
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").toImmutable();
    }

    public static DeviceLocation of(DeviceRef ref) {
        Objects.requireNonNull(ref, "ref");
        return new DeviceLocation(ref.dimension(), ref.position());
    }
}
