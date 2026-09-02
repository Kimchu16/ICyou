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

/**
 * Item data components. Components are the 1.21 replacement for arbitrary
 * NBT on items: they survive stack splitting and are network-synced.
 */
public final class ModDataComponentTypes {

    private ModDataComponentTypes() {}

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
                Identifier.of(ICyouMod.MOD_ID, "linked_camera"), LINKED_CAMERA);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_terminal"), LINKED_TERMINAL);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "linked_screen"), LINKED_SCREEN);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "wireless_id"), WIRELESS_ID);
    }
}
