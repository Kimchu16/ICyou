package com.matissjurevics.icyou.screen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

import com.matissjurevics.icyou.client.render.RttFeedManager;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import com.matissjurevics.icyou.terminal.DeviceRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * A passive display panel: pairs with the nearest camera terminal and renders
 * that terminal's selected camera feed on its face. Screens hold no cameras
 * themselves — see {@link CameraTerminalBlockEntity} (the hub) and the
 * portable screen item (the viewport).
 */
public class ScreenBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    private static final VoxelShape NORTH_SHAPE =
            Block.createCuboidShape(0, 0, 12.65, 16, 16, 16);
    private static final VoxelShape EAST_SHAPE =
            Block.createCuboidShape(0, 0, 0, 3.35, 16, 16);
    private static final VoxelShape SOUTH_SHAPE =
            Block.createCuboidShape(0, 0, 0, 16, 16, 3.35);
    private static final VoxelShape WEST_SHAPE =
            Block.createCuboidShape(12.65, 0, 0, 16, 16, 16);

    public ScreenBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // The clicked wall face is both the side presented to the player and the
        // direction away from the supporting block. Reject floor/ceiling placement.
        Direction facing = ctx.getSide();
        if (facing.getAxis().isVertical()) {
            return null;
        }

        BlockState state = getDefaultState().with(FACING, facing);
        return state.canPlaceAt(ctx.getWorld(), ctx.getBlockPos()) ? state : null;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        return world.getBlockState(supportPos)
                .isSideSolidFullSquare(world, supportPos, facing);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
                                                    BlockState neighborState, WorldAccess world,
                                                    BlockPos pos, BlockPos neighborPos) {
        if (direction == state.get(FACING).getOpposite()
                && !state.canPlaceAt(world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(
                state, direction, neighborState, world, pos, neighborPos);
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
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> throw new IllegalStateException("Screen has a vertical facing");
        };
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (type != ModBlockEntities.SCREEN) {
            return null;
        }
        if (world.isClient) {
            @SuppressWarnings("unchecked")
            BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                    (BlockEntityTicker<ScreenBlockEntity>) (w, p, s, be) ->
                            RttFeedManager.track(be);
            return ticker;
        }
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<ScreenBlockEntity>) ScreenBlockEntity::serverTick;
        return ticker;
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            DeviceRegistry.get((ServerWorld) world).removeScreen(pos);
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        // Passive display — right-click just reports pairing status.
        if (!world.isClient && world.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
            if (screen.isReceiving()) {
                player.sendMessage(Text.literal(String.format(
                        "Display live — CAM %d/%d", screen.getLastIndex(), screen.getLastCount())),
                        true);
            } else {
                player.sendMessage(Text.literal(
                        "No feed — link this screen to a terminal with the Setup Remote,"
                                + " then assign a camera in the terminal GUI."), true);
            }
        }
        return ActionResult.SUCCESS;
    }
}
