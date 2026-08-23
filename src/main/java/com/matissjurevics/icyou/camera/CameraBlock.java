package com.matissjurevics.icyou.camera;

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
import net.minecraft.world.BlockView;

/**
 * A wall-mountable security camera. The {@code facing} property indicates which
 * direction the lens points; placement points the lens at what the player looks at.
 */
public class CameraBlock extends Block {

    public static final DirectionProperty FACING = FacingBlock.FACING;

    // Outline shapes per horizontal facing (lens side extends away from the wall).
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(3, 5, 3, 13, 12, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(3, 5, 0, 13, 12, 13);
    private static final VoxelShape EAST_SHAPE  = Block.createCuboidShape(0, 5, 3, 13, 12, 13);
    private static final VoxelShape WEST_SHAPE  = Block.createCuboidShape(3, 5, 3, 16, 12, 13);

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
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                         ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }
}
