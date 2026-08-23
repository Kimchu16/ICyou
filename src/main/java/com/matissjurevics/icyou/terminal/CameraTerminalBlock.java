package com.matissjurevics.icyou.terminal;

import com.matissjurevics.icyou.camera.CameraViews;
import com.matissjurevics.icyou.network.DeviceSubscriptions;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * The security hub — a laptop-style terminal. Right-click opens the device
 * management GUI; sneak-click prints a status report.
 */
public class CameraTerminalBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = FacingBlock.FACING;

    // Model natively faces SOUTH (screen on the high-z side).
    private static final VoxelShape NS_SHAPE = Block.createCuboidShape(1, 0, 1, 15, 10.5, 13.5);
    private static final VoxelShape EW_SHAPE = Block.createCuboidShape(1, 0, 1, 13.5, 10.5, 15);

    public CameraTerminalBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.SOUTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
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
            case EAST, WEST -> EW_SHAPE;
            default -> NS_SHAPE;
        };
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CameraTerminalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                 BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        ServerWorld serverWorld = (ServerWorld) world;

        if (player.isSneaking()) {
            player.sendMessage(Text.literal("── ICyou LIVE ──"), false);
            int index = 1;
            for (BlockPos camera : new CameraTerminalBlockEntity(pos, state)
                    .getCameras(serverWorld)) {
                player.sendMessage(CameraViews.describe(world, camera, index++), false);
            }
            return ActionResult.SUCCESS;
        }

        DeviceSubscriptions.subscribe(pos, player.getUuid());
        ServerPlayNetworking.send((ServerPlayerEntity) player,
                DeviceSubscriptions.buildSnapshot(serverWorld, pos, true));
        return ActionResult.SUCCESS;
    }
}
