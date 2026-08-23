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
            if (terminal.getCount() == 0) {
                player.sendMessage(Text.literal("No cameras linked."), false);
                return ActionResult.SUCCESS;
            }

            // Sneak-use prints the full status report; plain use cycles channels.
            if (player.isSneaking()) {
                player.sendMessage(Text.literal("── ICyou LIVE ──"), false);
                int index = 1;
                for (BlockPos camera : terminal.getCameras()) {
                    player.sendMessage(CameraViews.describe(world, camera, index++), false);
                }
            } else {
                CameraTerminalBlockEntity.BoundCamera current = terminal.cycleSelected(world);
                player.sendMessage(Text.literal(String.format("Switched to CAM %d/%d [%s]",
                        current.index(), current.count(), current.facing().asString())), true);
            }
        }
        return ActionResult.SUCCESS;
    }
}
