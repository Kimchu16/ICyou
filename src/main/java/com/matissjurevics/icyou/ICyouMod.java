package com.matissjurevics.icyou;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class ICyouMod implements ModInitializer {

    public static final String MOD_ID = "icyou";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // The custom blocks for this mod.
    public static final Block ICY_BLOCK = new IcyBlock(
            Block.Settings.create()
                    .strength(2.0f, 2.0f)
    );

    public static final Block GLACIER_BLOCK = new GlacierBlock(
            Block.Settings.create()
                    .strength(2.5f, 3.0f)
    );

    @Override
    public void onInitialize() {
        // --- Register the blocks ---
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "icy_block"), ICY_BLOCK);
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "glacier_block"), GLACIER_BLOCK);

        // --- Register the blocks' items ---
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "icy_block"),
                new BlockItem(ICY_BLOCK, new Item.Settings()));
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "glacier_block"),
                new BlockItem(GLACIER_BLOCK, new Item.Settings()));

        // --- Add the blocks to the Building Blocks creative tab ---
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS)
                .register(entries -> {
                    entries.add(ICY_BLOCK);
                    entries.add(GLACIER_BLOCK);
                });

        // --- Register the test command: /ictest ---
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("ictest")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            player.getInventory().offerOrDrop(new ItemStack(ICY_BLOCK, 16));
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
                                                    ICY_BLOCK.getDefaultState());
                                        }
                                    }
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("Placed a 5x5 platform of Icy Blocks!"), true);
                                    return 1;
                                }))));

        LOGGER.info("ICyou has been initialized!");
    }
}
