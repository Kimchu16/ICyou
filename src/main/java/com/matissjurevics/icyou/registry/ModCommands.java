package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.ICyouMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static net.minecraft.server.command.CommandManager.literal;

/** All commands registered by the mod. */
public final class ModCommands {

    private ModCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("ictest")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            player.getInventory().offerOrDrop(
                                    new ItemStack(ModBlocks.ICY_BLOCK, 16));
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("Gave you 16 Icy Blocks!"), false);
                            return 1;
                        })
                        .then(literal("place")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    World world = player.getWorld();
                                    BlockPos base = player.getBlockPos();
                                    // Place a 5x5 platform of Icy Blocks just below the player.
                                    for (int dx = -2; dx <= 2; dx++) {
                                        for (int dz = -2; dz <= 2; dz++) {
                                            world.setBlockState(base.add(dx, -1, dz),
                                                    ModBlocks.ICY_BLOCK.getDefaultState());
                                        }
                                    }
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("Placed a 5x5 platform of Icy Blocks!"),
                                            true);
                                    return 1;
                                }))));
    }
}
