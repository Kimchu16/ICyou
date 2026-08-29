package com.matissjurevics.icyou.remote;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.registry.ModDataComponentTypes;
import com.matissjurevics.icyou.screen.ScreenBlock;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import com.matissjurevics.icyou.terminal.DeviceRegistry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Setup Remote: pick up a camera (use on camera) or a screen link (use on
 * screen), then deliver it to a camera terminal.
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

        // --- Pick up a camera link ---
        if (world.getBlockState(pos).getBlock() instanceof CameraBlock) {
            if (!world.isClient) {
                stack.set(ModDataComponentTypes.LINKED_CAMERA, pos.toImmutable());
                if (player != null) {
                    player.sendMessage(Text.literal("Camera link picked up at " + pos.toShortString()), true);
                }
            }
            return ActionResult.SUCCESS;
        }

        // --- Pick up a screen link ---
        if (world.getBlockState(pos).getBlock() instanceof ScreenBlock screenBlock) {
            if (!world.isClient) {
                BlockPos controllerPos = screenBlock.getControllerPos(
                        world.getBlockState(pos), pos);
                stack.set(ModDataComponentTypes.LINKED_SCREEN,
                        controllerPos.toImmutable());
                if (player != null) {
                    player.sendMessage(Text.literal("Screen link picked up at "
                            + controllerPos.toShortString()), true);
                }
            }
            return ActionResult.SUCCESS;
        }

        // --- Deliver to a terminal ---
        if (world.getBlockState(pos).getBlock() instanceof CameraTerminalBlock) {
            if (!world.isClient) {
                DeviceRegistry reg = DeviceRegistry.get((ServerWorld) world);
                BlockPos cam = stack.get(ModDataComponentTypes.LINKED_CAMERA);
                BlockPos scr = stack.get(ModDataComponentTypes.LINKED_SCREEN);
                if (player == null) {
                    return ActionResult.PASS;
                }
                if (cam != null) {
                    var dev = reg.addCamera(pos, cam);
                    stack.remove(ModDataComponentTypes.LINKED_CAMERA);
                    player.sendMessage(Text.literal("Camera linked as " + dev.name()), false);
                } else if (scr != null) {
                    var dev = reg.addScreen(pos, scr);
                    if (world.getBlockEntity(scr) instanceof ScreenBlockEntity screen) {
                        screen.setTerminal(pos);
                    }
                    stack.remove(ModDataComponentTypes.LINKED_SCREEN);
                    player.sendMessage(Text.literal("Screen linked as " + dev.name()), false);
                } else {
                    player.sendMessage(Text.literal(
                            "Nothing to link — use the remote on a camera or screen first."), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
