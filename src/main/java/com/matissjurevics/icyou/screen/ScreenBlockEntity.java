package com.matissjurevics.icyou.screen;

import java.util.Optional;

import com.matissjurevics.icyou.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Holds the camera a screen displays. The camera's facing is captured at bind
 * time so the client renderer never needs to load the camera's chunk.
 */
public class ScreenBlockEntity extends BlockEntity {

    private BlockPos linkedCamera;
    private Direction cameraFacing = Direction.NORTH;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN, pos, state);
    }

    public void bind(BlockPos cameraPos, Direction cameraFacing) {
        this.linkedCamera = cameraPos.toImmutable();
        this.cameraFacing = cameraFacing;
        markDirty();
    }

    public Optional<BlockPos> getLinkedCamera() {
        return Optional.ofNullable(linkedCamera);
    }

    public Direction getCameraFacing() {
        return cameraFacing;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        if (linkedCamera != null) {
            nbt.putLong("camera", linkedCamera.asLong());
            nbt.putString("cam_facing", cameraFacing.getName());
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        if (nbt.contains("camera")) {
            linkedCamera = BlockPos.fromLong(nbt.getLong("camera"));
            cameraFacing = Direction.byName(nbt.getString("cam_facing"));
            if (cameraFacing == null) {
                cameraFacing = Direction.NORTH;
            }
        } else {
            linkedCamera = null;
        }
    }
}
