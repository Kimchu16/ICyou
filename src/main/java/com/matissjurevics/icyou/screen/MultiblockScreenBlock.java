package com.matissjurevics.icyou.screen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Door-style display made from one block-sized model per occupied position.
 * The bottom-left part is the controller and the only part with a block entity.
 */
public class MultiblockScreenBlock extends ScreenBlock {

    public static final EnumProperty<ScreenPart> PART =
            EnumProperty.of("part", ScreenPart.class);

    private final int size;

    public MultiblockScreenBlock(int size, Settings settings) {
        super(settings);
        if (size != 2 && size != 3) {
            throw new IllegalArgumentException("Multiblock displays must be 2x2 or 3x3");
        }
        this.size = size;
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, ScreenPart.BOTTOM_LEFT));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public int getDisplayWidth() {
        return size;
    }

    @Override
    public int getDisplayHeight() {
        return size;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        Direction facing = context.getHorizontalPlayerFacing().getOpposite();
        BlockState anchorState = getDefaultState()
                .with(FACING, facing)
                .with(PART, ScreenPart.BOTTOM_LEFT);
        return canPlaceWholeDisplay(context, facing) ? anchorState : null;
    }

    private boolean canPlaceWholeDisplay(ItemPlacementContext context, Direction facing) {
        World world = context.getWorld();
        BlockPos anchor = context.getBlockPos();
        Direction right = facing.rotateYCounterclockwise();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                BlockPos partPos = anchor.offset(right, column).up(row);
                if (!world.isInBuildLimit(partPos)
                        || !world.getBlockState(partPos).isReplaceable()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        Direction facing = state.get(FACING);
        Direction right = facing.rotateYCounterclockwise();

        // Suppress neighbor updates until every part exists. This prevents the
        // completeness check from dismantling a half-constructed display.
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (column == 0 && row == 0) {
                    continue;
                }
                BlockPos partPos = pos.offset(right, column).up(row);
                world.setBlockState(partPos,
                        state.with(PART, ScreenPart.at(column, row)),
                        Block.NOTIFY_LISTENERS);
            }
        }
        forEachPart(pos, facing, partPos -> world.updateNeighbors(partPos, this));
    }

    @Override
    public BlockPos getControllerPos(BlockState state, BlockPos pos) {
        ScreenPart part = state.get(PART);
        Direction right = state.get(FACING).rotateYCounterclockwise();
        return pos.offset(right.getOpposite(), part.column()).down(part.row());
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(PART) == ScreenPart.BOTTOM_LEFT
                ? new ScreenBlockEntity(pos, state)
                : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        return state.get(PART) == ScreenPart.BOTTOM_LEFT
                ? super.getTicker(world, state, type)
                : null;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
                                                    BlockState neighborState, WorldAccess world,
                                                    BlockPos pos, BlockPos neighborPos) {
        return isComplete(world, getControllerPos(state, pos), state.get(FACING))
                ? state
                : Blocks.AIR.getDefaultState();
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos anchor = getControllerPos(state, pos);
        Direction facing = state.get(FACING);

        if (!world.isClient) {
            forEachPart(anchor, facing, partPos -> {
                if (partPos.equals(pos)) {
                    return;
                }
                BlockState partState = world.getBlockState(partPos);
                if (partState.isOf(this)) {
                    world.setBlockState(partPos, Blocks.AIR.getDefaultState(),
                            Block.NOTIFY_ALL);
                }
            });
            if (!player.isInCreativeMode()) {
                Block.dropStack(world, anchor, new ItemStack(this));
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    private boolean isComplete(WorldAccess world, BlockPos anchor, Direction facing) {
        Direction right = facing.rotateYCounterclockwise();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                BlockState partState = world.getBlockState(
                        anchor.offset(right, column).up(row));
                if (!partState.isOf(this)
                        || partState.get(FACING) != facing
                        || partState.get(PART) != ScreenPart.at(column, row)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void forEachPart(BlockPos anchor, Direction facing,
                             java.util.function.Consumer<BlockPos> action) {
        Direction right = facing.rotateYCounterclockwise();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                action.accept(anchor.offset(right, column).up(row));
            }
        }
    }
}
