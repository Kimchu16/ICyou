package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.ICyouMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Standalone items (i.e. anything that is not a {@link BlockItem}). */
public final class ModItems {

    private ModItems() {}

    public static final Item PORTABLE_SCREEN = register("portable_screen",
            new Item(new Item.Settings()));

    public static final Item SETUP_REMOTE = register("setup_remote",
            new Item(new Item.Settings()));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(ICyouMod.MOD_ID, name), item);
    }

    /** Called by the mod entrypoint; see {@link ModBlocks#register()}. */
    public static void register() {}
}
