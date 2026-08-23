package com.matissjurevics.icyou.terminal;

import java.util.Collections;
import java.util.List;

import com.matissjurevics.icyou.registry.ModBlockEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

/**
 * Thin block entity for the terminal. Device state lives in the world-level
 * {@link DeviceRegistry}, so this only needs to exist for the block to have a
 * block entity (and for future per-terminal GUI state).
 */
public class CameraTerminalBlockEntity extends BlockEntity {

    public CameraTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_TERMINAL, pos, state);
    }

    /** All camera positions registered to this terminal. */
    public List<BlockPos> getCameras(ServerWorld world) {
        return DeviceRegistry.get(world).camerasFor(pos).stream()
                .map(c -> c.pos()).toList();
    }

    public int getCount(ServerWorld world) {
        return DeviceRegistry.get(world).camerasFor(pos).size();
    }
}
