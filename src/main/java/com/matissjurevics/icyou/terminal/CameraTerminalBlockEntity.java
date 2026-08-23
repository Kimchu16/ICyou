package com.matissjurevics.icyou.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The security hub. Terminals own the camera list; screens pair to a terminal
 * and display the camera the terminal currently has selected.
 */
public class CameraTerminalBlockEntity extends BlockEntity {

    /** Maximum number of cameras one terminal can hold. */
    public static final int MAX_CAMERAS = 8;

    /** A camera reference together with its facing and channel position. */
    public record BoundCamera(BlockPos pos, Direction facing, int index, int count) {}

    private final List<BlockPos> cameras = new ArrayList<>();
    private int selectedIndex;

    public CameraTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_TERMINAL, pos, state);
    }

    // --- Channel management ---

    /** @return false if the terminal already holds {@link #MAX_CAMERAS} cameras. */
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

    public int getCount() {
        return cameras.size();
    }

    public List<BlockPos> getCameras() {
        return Collections.unmodifiableList(cameras);
    }

    /** Advances the selected channel (wrapping) and returns it; null if empty. */
    public BoundCamera cycleSelected(World world) {
        if (cameras.isEmpty()) {
            return null;
        }
        selectedIndex = Math.floorMod(selectedIndex + 1, cameras.size());
        markDirty();
        return getSelected(world);
    }

    /** The currently selected camera with facing resolved from the world. */
    public BoundCamera getSelected(World world) {
        if (cameras.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(selectedIndex, cameras.size());
        BlockPos pos = cameras.get(index);
        Direction facing = Direction.NORTH;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof CameraBlock) {
            facing = state.get(CameraBlock.FACING);
        }
        return new BoundCamera(pos, facing, index + 1, cameras.size());
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putLongArray("cameras",
                cameras.stream().mapToLong(BlockPos::asLong).toArray());
        nbt.putInt("selected", selectedIndex);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        cameras.clear();
        for (long encoded : nbt.getLongArray("cameras")) {
            cameras.add(BlockPos.fromLong(encoded));
        }
        selectedIndex = Math.floorMod(nbt.getInt("selected"), Math.max(1, cameras.size()));
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        return createNbt(lookup);
    }
}
