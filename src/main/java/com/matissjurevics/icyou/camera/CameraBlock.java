package com.matissjurevics.icyou.camera;

import java.time.Instant;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.DeviceLocation;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A wall-mountable security camera. The {@code facing} property indicates which
 * direction the lens points; placement points the lens at what the player looks at.
 */
public class CameraBlock extends Block {

    public static final DirectionProperty FACING = FacingBlock.FACING;

    // Outline shapes per horizontal facing (lens side extends away from the wall;
    // body hugs x/z centre with a ceiling mount reaching the top of the space).
    private static final VoxelShape NS_SHAPE = Block.createCuboidShape(3.5, 7, 0, 12.5, 16, 16);
    private static final VoxelShape EW_SHAPE = Block.createCuboidShape(0, 7, 3.5, 16, 16, 12.5);

    public CameraBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Point the lens towards whatever the player is looking at.
        return getDefaultState().with(FACING, ctx.getPlayerLookDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            ServerWorld serverWorld = (ServerWorld) world;
            GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(serverWorld.getServer());
            registry.deviceAt(new DeviceLocation(serverWorld.getRegistryKey(), pos))
                    .filter(CameraRef.class::isInstance).map(CameraRef.class::cast)
                    .ifPresent(ref -> registry.tombstoneCamera(ref.deviceId(), Instant.now()));
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                         ShapeContext context) {
        return switch (state.get(FACING)) {
            case EAST, WEST -> EW_SHAPE;
            default -> NS_SHAPE;
        };
    }
}
