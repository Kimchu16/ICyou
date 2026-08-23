package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.ICyouMod;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Item data components. Components are the 1.21 replacement for arbitrary
 * NBT on items: they survive stack splitting and are network-synced.
 */
public final class ModDataComponentTypes {

    private ModDataComponentTypes() {}

    /** Camera position currently carried by a Setup Remote. */
    public static final ComponentType<BlockPos> LINKED_CAMERA = ComponentType.<BlockPos>builder()
            .codec(BlockPos.CODEC)
            .packetCodec(BlockPos.PACKET_CODEC)
            .build();

    public static void register() {
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_camera"), LINKED_CAMERA);
    }
}
