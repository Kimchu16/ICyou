package com.matissjurevics.icyou.network;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.device.TerminalRef;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: subscribe/unsubscribe to a terminal's device snapshot
 * broadcast (opened GUI or open portable-screen HUD).
 */
public record DeviceSubscribeC2SPayload(TerminalRef terminal, boolean subscribe)
        implements CustomPayload {

    public static final CustomPayload.Id<DeviceSubscribeC2SPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "device_subscribe"));

    public static final PacketCodec<RegistryByteBuf, DeviceSubscribeC2SPayload> CODEC =
            PacketCodec.of(DeviceSubscribeC2SPayload::write, DeviceSubscribeC2SPayload::read);

    private static void write(DeviceSubscribeC2SPayload p, RegistryByteBuf buf) {
        TerminalRef.PACKET_CODEC.encode(buf, p.terminal());
        buf.writeBoolean(p.subscribe());
    }

    private static DeviceSubscribeC2SPayload read(RegistryByteBuf buf) {
        return new DeviceSubscribeC2SPayload(TerminalRef.PACKET_CODEC.decode(buf),
                buf.readBoolean());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
