package com.matissjurevics.icyou.item;

import com.matissjurevics.icyou.client.hud.WirelessHud;
import com.matissjurevics.icyou.registry.ModDataComponentTypes;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import com.matissjurevics.icyou.terminal.DeviceRegistry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Portable Screen: sneak-use on a camera terminal to pair with it, then
 * right-click in the air to open the camera HUD list; press 1-8 to view.
 */
public class PortableScreenItem extends Item {

    public PortableScreenItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        ItemStack stack = context.getStack();
        PlayerEntity player = context.getPlayer();

        if (world.getBlockState(pos).getBlock() instanceof CameraTerminalBlock) {
            if (!world.isClient) {
                var reg = DeviceRegistry.get((ServerWorld) world);
                var dev = reg.addWireless(pos);
                stack.set(ModDataComponentTypes.LINKED_TERMINAL, pos.toImmutable());
                stack.set(ModDataComponentTypes.WIRELESS_ID, dev.id());
                if (player != null) {
                    player.sendMessage(Text.literal("Paired as " + dev.name()), true);
                }
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            BlockPos terminal = stack.get(ModDataComponentTypes.LINKED_TERMINAL);
            if (terminal != null) {
                WirelessHud.toggle(terminal);
                return TypedActionResult.success(stack, true);
            }
            return TypedActionResult.pass(stack);
        }

        // Server side: validate pairing.
        BlockPos terminal = stack.get(ModDataComponentTypes.LINKED_TERMINAL);
        if (terminal == null) {
            user.sendMessage(Text.literal(
                    "Not paired — sneak-use this screen on a camera terminal first."), false);
            return TypedActionResult.fail(stack);
        }
        return TypedActionResult.success(stack, false);
    }
}
