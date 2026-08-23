package com.matissjurevics.icyou.screen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * A wall-mountable display panel. Bind a camera to it with the Setup Remote
 * and its face renders that camera's live feed (see the client BER).
 */
public class ScreenBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = FacingBlock.FACING;

    private static final VoxelShape NS_SHAPE = Block.createCuboidShape(2, 2, 5, 14, 14, 11);
    private static final VoxelShape EW_SHAPE = Block.createCuboidShape(5, 2, 2, 11, 14, 14);

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
        // Display faces the player when placed.
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
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
            if (screen.getLinkedCamera().isEmpty()) {
                player.sendMessage(Text.literal("No camera bound to this screen."), false);
            } else {
                BlockPos cam = screen.getLinkedCamera().get();
                // Signal check before detaching their view.
                if (world.getBlockState(cam).getBlock() instanceof CameraBlock) {
                    ServerPlayNetworking.send((ServerPlayerEntity) player,
                            new EnterCameraViewS2CPayload(cam,
                                    screen.getCameraFacing().getId()));
                } else {
                    player.sendMessage(Text.literal("Signal lost."), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (!world.isClient && type == ModBlockEntities.SCREEN) {
            @SuppressWarnings("unchecked")
            BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                    (BlockEntityTicker<ScreenBlockEntity>) ScreenBlockEntity::serverTick;
            return ticker;
        }
        return null;
    }
}
