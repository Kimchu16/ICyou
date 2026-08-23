package com.matissjurevics.icyou.terminal;

import java.util.List;

import com.matissjurevics.icyou.camera.CameraViews;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Camera terminal: right-click to view the live stylized feed of every
 * linked camera.
 */
public class CameraTerminalBlock extends Block implements BlockEntityProvider {

    public CameraTerminalBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CameraTerminalBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof CameraTerminalBlockEntity terminal) {
            player.sendMessage(Text.literal("── ICyou LIVE ──"), false);

            List<BlockPos> cameras = terminal.getCameras();
            if (cameras.isEmpty()) {
                player.sendMessage(Text.literal("No cameras linked."), false);
            } else {
                int index = 1;
                for (BlockPos camera : cameras) {
                    player.sendMessage(CameraViews.describe(world, camera, index++), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }
}
