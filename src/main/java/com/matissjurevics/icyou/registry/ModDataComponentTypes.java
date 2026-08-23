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

    /** Terminal position a Portable Screen is paired with. */
    public static final ComponentType<BlockPos> LINKED_TERMINAL = ComponentType.<BlockPos>builder()
            .codec(BlockPos.CODEC)
            .packetCodec(BlockPos.PACKET_CODEC)
            .build();

    /** Screen position currently carried by a Setup Remote. */
    public static final ComponentType<BlockPos> LINKED_SCREEN = ComponentType.<BlockPos>builder()
            .codec(BlockPos.CODEC)
            .packetCodec(BlockPos.PACKET_CODEC)
            .build();

    /** Wireless device id assigned to a paired Portable Screen. */
    public static final ComponentType<Integer> WIRELESS_ID = ComponentType.<Integer>builder()
            .codec(com.mojang.serialization.Codec.INT)
            .packetCodec(net.minecraft.network.codec.PacketCodecs.VAR_INT)
            .build();

    public static void register() {
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_camera"), LINKED_CAMERA);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_terminal"), LINKED_TERMINAL);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_screen"), LINKED_SCREEN);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "wireless_id"), WIRELESS_ID);
    }
}
