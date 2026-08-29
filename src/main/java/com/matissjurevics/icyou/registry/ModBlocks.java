package com.matissjurevics.icyou.registry;

import java.util.function.Function;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.screen.ScreenBlock;
import com.matissjurevics.icyou.screen.MultiblockScreenBlock;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * All blocks in the mod. Blocks with an item form get their {@link BlockItem}
 * registered automatically by {@link #register}.
 */
public final class ModBlocks {

    private ModBlocks() {}

    // --- Security devices ---

    public static final Block CAMERA = register("camera", CameraBlock::new,
            AbstractBlock.Settings.create().strength(2.0f, 2.0f));

    public static final Block CAMERA_TERMINAL = register("camera_terminal", CameraTerminalBlock::new,
            AbstractBlock.Settings.create().strength(2.0f, 2.0f));

    public static final Block SCREEN = register("screen", ScreenBlock::new,
            // The wall display only occupies a thin slice of its block space.
            // Non-opaque prevents Minecraft from culling the supporting block's
            // face as though the display were a full cube.
            AbstractBlock.Settings.create().strength(2.0f, 2.0f).nonOpaque());

    public static final Block MEDIUM_SCREEN = register("medium_screen",
            settings -> new MultiblockScreenBlock(2, settings),
            AbstractBlock.Settings.create().strength(2.0f, 2.0f).nonOpaque());

    public static final Block BIG_SCREEN = register("big_screen",
            settings -> new MultiblockScreenBlock(3, settings),
            AbstractBlock.Settings.create().strength(2.0f, 2.0f).nonOpaque());

    private static Block register(String name, Function<AbstractBlock.Settings, Block> factory,
                                  AbstractBlock.Settings settings) {
        Block block = factory.apply(settings);
        Registry.register(Registries.BLOCK, Identifier.of(ICyouMod.MOD_ID, name), block);
        Registry.register(Registries.ITEM, Identifier.of(ICyouMod.MOD_ID, name),
                new BlockItem(block, new Item.Settings()));
        return block;
    }

    /**
     * Called by the mod entrypoint. Loading this class triggers the static
     * field initialisers above, which perform the actual registration.
     */
    public static void register() {}
}
