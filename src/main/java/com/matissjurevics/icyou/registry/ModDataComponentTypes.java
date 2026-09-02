package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.ScreenRef;
import com.matissjurevics.icyou.device.TerminalRef;
import java.util.UUID;
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

    /** 0.2.0 position component retained so old items can be read and upgraded. */
    public static final ComponentType<BlockPos> LEGACY_LINKED_CAMERA =
            ComponentType.<BlockPos>builder().codec(BlockPos.CODEC)
                    .packetCodec(BlockPos.PACKET_CODEC).build();

    /** 0.2.0 terminal position retained so old portable screens can be read. */
    public static final ComponentType<BlockPos> LEGACY_LINKED_TERMINAL =
            ComponentType.<BlockPos>builder().codec(BlockPos.CODEC)
                    .packetCodec(BlockPos.PACKET_CODEC).build();

    /** 0.2.0 screen position retained so old remotes can be read and upgraded. */
    public static final ComponentType<BlockPos> LEGACY_LINKED_SCREEN =
            ComponentType.<BlockPos>builder().codec(BlockPos.CODEC)
                    .packetCodec(BlockPos.PACKET_CODEC).build();

    /** 0.2.0 integer wireless ID retained for safe item deserialization. */
    public static final ComponentType<Integer> LEGACY_WIRELESS_ID =
            ComponentType.<Integer>builder().codec(com.mojang.serialization.Codec.INT)
                    .packetCodec(net.minecraft.network.codec.PacketCodecs.VAR_INT).build();

    /** Stable camera reference currently carried by a Setup Remote. */
    public static final ComponentType<CameraRef> LINKED_CAMERA = ComponentType.<CameraRef>builder()
            .codec(CameraRef.CODEC)
            .packetCodec(CameraRef.PACKET_CODEC)
            .build();

    /** Stable terminal reference a Portable Screen is paired with. */
    public static final ComponentType<TerminalRef> LINKED_TERMINAL = ComponentType.<TerminalRef>builder()
            .codec(TerminalRef.CODEC)
            .packetCodec(TerminalRef.PACKET_CODEC)
            .build();

    /** Stable screen reference currently carried by a Setup Remote. */
    public static final ComponentType<ScreenRef> LINKED_SCREEN = ComponentType.<ScreenRef>builder()
            .codec(ScreenRef.CODEC)
            .packetCodec(ScreenRef.PACKET_CODEC)
            .build();

    /** Wireless device id assigned to a paired Portable Screen. */
    public static final ComponentType<UUID> WIRELESS_ID = ComponentType.<UUID>builder()
            .codec(net.minecraft.util.Uuids.CODEC)
            .packetCodec(net.minecraft.util.Uuids.PACKET_CODEC)
            .build();

    public static void register() {
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_camera"), LEGACY_LINKED_CAMERA);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_terminal"), LEGACY_LINKED_TERMINAL);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_screen"), LEGACY_LINKED_SCREEN);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "wireless_id"), LEGACY_WIRELESS_ID);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_camera_ref"), LINKED_CAMERA);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_terminal_ref"), LINKED_TERMINAL);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_screen_ref"), LINKED_SCREEN);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "wireless_uuid"), WIRELESS_ID);
    }
}
