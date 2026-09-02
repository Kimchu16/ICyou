package com.matissjurevics.icyou.item;

import com.matissjurevics.icyou.client.hud.WirelessHud;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.device.TerminalRef;
import com.matissjurevics.icyou.registry.ModDataComponentTypes;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import com.matissjurevics.icyou.terminal.CameraTerminalBlockEntity;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
                ServerWorld serverWorld = (ServerWorld) world;
                if (!(world.getBlockEntity(pos) instanceof CameraTerminalBlockEntity terminal)) {
                    return ActionResult.FAIL;
                }
                TerminalRef terminalRef = terminal.initialize(serverWorld);
                UUID wirelessId = UUID.randomUUID();
                stack.set(ModDataComponentTypes.LINKED_TERMINAL, terminalRef);
                stack.set(ModDataComponentTypes.WIRELESS_ID, wirelessId);
                stack.remove(ModDataComponentTypes.LEGACY_LINKED_TERMINAL);
                stack.remove(ModDataComponentTypes.LEGACY_WIRELESS_ID);
                if (player != null) {
                    player.sendMessage(Text.literal("Portable screen paired"), true);
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
            TerminalRef terminal = stack.get(ModDataComponentTypes.LINKED_TERMINAL);
            if (terminal != null) {
                WirelessHud.toggle(terminal);
                return TypedActionResult.success(stack, true);
            }
            return TypedActionResult.pass(stack);
        }

        // Server side: upgrade a 0.2.0 position/int link, then validate pairing.
        if (world instanceof ServerWorld serverWorld) {
            upgradeLegacyLink(stack, serverWorld);
        }
        TerminalRef terminal = stack.get(ModDataComponentTypes.LINKED_TERMINAL);
        if (terminal == null) {
            user.sendMessage(Text.literal(
                    "Not paired — sneak-use this screen on a camera terminal first."), false);
            return TypedActionResult.fail(stack);
        }
        if (world instanceof ServerWorld serverWorld
                && GlobalDeviceRegistry.get(serverWorld.getServer())
                .terminal(terminal.deviceId()).filter(entry -> entry.ref().equals(terminal)).isEmpty()) {
            user.sendMessage(Text.literal("Paired terminal is unavailable."), false);
            return TypedActionResult.fail(stack);
        }
        return TypedActionResult.success(stack, false);
    }

    private static void upgradeLegacyLink(ItemStack stack, ServerWorld world) {
        if (stack.get(ModDataComponentTypes.LINKED_TERMINAL) != null) {
            return;
        }
        BlockPos position = stack.get(ModDataComponentTypes.LEGACY_LINKED_TERMINAL);
        if (position == null) {
            return;
        }
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(world.getServer());
        registry.deviceAt(new com.matissjurevics.icyou.device.DeviceLocation(
                        world.getRegistryKey(), position))
                .filter(TerminalRef.class::isInstance)
                .map(TerminalRef.class::cast)
                .ifPresent(terminal -> {
                    stack.set(ModDataComponentTypes.LINKED_TERMINAL, terminal);
                    Integer legacyId = stack.get(ModDataComponentTypes.LEGACY_WIRELESS_ID);
                    String source = "icyou:0.2.0:wireless:" + world.getRegistryKey().getValue()
                            + ':' + (legacyId == null ? 0 : legacyId) + ':' + position.asLong();
                    stack.set(ModDataComponentTypes.WIRELESS_ID, UUID.nameUUIDFromBytes(
                            source.getBytes(StandardCharsets.UTF_8)));
                    stack.remove(ModDataComponentTypes.LEGACY_LINKED_TERMINAL);
                    stack.remove(ModDataComponentTypes.LEGACY_WIRELESS_ID);
                });
    }
}
