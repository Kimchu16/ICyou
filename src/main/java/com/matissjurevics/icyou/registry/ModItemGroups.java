package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.ICyouMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The dedicated "ICyou" creative tab containing every item the mod adds. */
public final class ModItemGroups {

    private ModItemGroups() {}

    public static final RegistryKey<ItemGroup> ICYOU_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(ICyouMod.MOD_ID, "main"));

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, ICYOU_KEY,
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.icyou.main"))
                        .icon(() -> new ItemStack(ModBlocks.CAMERA))
                        .entries((context, entries) -> {
                            entries.add(ModBlocks.CAMERA);
                            entries.add(ModBlocks.CAMERA_TERMINAL);
                            entries.add(ModBlocks.SCREEN);
                            entries.add(ModItems.PORTABLE_SCREEN);
                            entries.add(ModItems.SETUP_REMOTE);
                        })
                        .build());
    }
}
