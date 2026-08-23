package com.matissjurevics.icyou.remote;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.registry.ModDataComponentTypes;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import com.matissjurevics.icyou.terminal.CameraTerminalBlockEntity;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Setup Remote: right-click a camera to pick it up on the remote, then
 * right-click a camera terminal to add it to the terminal's channel list.
 */
public class SetupRemoteItem extends Item {

    public SetupRemoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        ItemStack stack = context.getStack();
        var player = context.getPlayer();

        // --- Link the remote to a camera ---
        if (world.getBlockState(pos).getBlock() instanceof CameraBlock) {
            if (!world.isClient) {
                stack.set(ModDataComponentTypes.LINKED_CAMERA, pos.toImmutable());
                if (player != null) {
                    player.sendMessage(Text.literal(
                            "Camera linked at " + formatPos(pos)), true);
                }
            }
            return ActionResult.SUCCESS;
        }

        // --- Add the carried camera to a terminal ---
        if (world.getBlockState(pos).getBlock() instanceof CameraTerminalBlock) {
            if (!world.isClient) {
                BlockPos carried = stack.get(ModDataComponentTypes.LINKED_CAMERA);
                if (player == null) {
                    return ActionResult.PASS;
                }
                if (carried == null) {
                    player.sendMessage(Text.literal(
                            "No camera linked — use the remote on a camera first."), false);
                    return ActionResult.FAIL;
                }
                if (world.getBlockEntity(pos) instanceof CameraTerminalBlockEntity terminal
                        && terminal.addCamera(carried.toImmutable())) {
                    stack.remove(ModDataComponentTypes.LINKED_CAMERA);
                    player.sendMessage(Text.literal("Camera added to terminal ("
                            + terminal.getCount() + " linked)"), false);
                } else {
                    player.sendMessage(Text.literal(
                            "Terminal is full (" + CameraTerminalBlockEntity.MAX_CAMERAS
                                    + " max)."), false);
                }
            }
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
