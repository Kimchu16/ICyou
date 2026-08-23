package com.matissjurevics.icyou.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.matissjurevics.icyou.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * Holds the cameras linked to a camera terminal.
 *
 * <p>Phase 1: positions are persisted to NBT and queried on demand. Later
 * phases can stream feed data from these positions instead.</p>
 */
public class CameraTerminalBlockEntity extends BlockEntity {

    /** Maximum number of cameras one terminal can bind. */
    public static final int MAX_CAMERAS = 8;

    private final List<BlockPos> cameras = new ArrayList<>();

    public CameraTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_TERMINAL, pos, state);
    }

    /** @return false if the terminal already holds the maximum number of cameras. */
    public boolean addCamera(BlockPos pos) {
        if (cameras.size() >= MAX_CAMERAS) {
            return false;
        }
        if (!cameras.contains(pos)) {
            cameras.add(pos);
        }
        markDirty();
        return true;
    }

    public List<BlockPos> getCameras() {
        return Collections.unmodifiableList(cameras);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putLongArray("cameras",
                cameras.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        cameras.clear();
        for (long encoded : nbt.getLongArray("cameras")) {
            cameras.add(BlockPos.fromLong(encoded));
        }
    }
}
