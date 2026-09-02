package com.matissjurevics.icyou.device;

import java.util.UUID;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Stable identity and current dimension-aware location of a placed device. */
public sealed interface DeviceRef permits CameraRef, TerminalRef, ScreenRef {

    UUID deviceId();

    RegistryKey<World> dimension();

    BlockPos position();
}
