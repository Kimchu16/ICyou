package com.matissjurevics.icyou.network;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client → server: a mutation performed in the terminal GUI.
 *
 * @param action 0 = assign camera to screen, 1 = rename, 2 = remove
 * @param targetType 0 = camera, 1 = screen, 2 = wireless
 * @param id target device id
 * @param auxId assign: camera id; unused otherwise
 * @param name rename: new name
 */
public record DeviceActionC2SPayload(BlockPos terminal, byte action, byte targetType,
                                     int id, int auxId, String name)
        implements CustomPayload {

    public static final byte ACTION_ASSIGN = 0;
    public static final byte ACTION_RENAME = 1;
    public static final byte ACTION_REMOVE = 2;

    public static final byte TYPE_CAMERA = 0;
    public static final byte TYPE_SCREEN = 1;
    public static final byte TYPE_WIRELESS = 2;

    public static final CustomPayload.Id<DeviceActionC2SPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "device_action"));

    public static final PacketCodec<RegistryByteBuf, DeviceActionC2SPayload> CODEC =
            PacketCodec.of(DeviceActionC2SPayload::write, DeviceActionC2SPayload::read);

    private static void write(DeviceActionC2SPayload p, RegistryByteBuf buf) {
        buf.writeBlockPos(p.terminal());
        buf.writeByte(p.action());
        buf.writeByte(p.targetType());
        buf.writeVarInt(p.id());
        buf.writeVarInt(p.auxId());
        buf.writeString(p.name());
    }

    private static DeviceActionC2SPayload read(RegistryByteBuf buf) {
        return new DeviceActionC2SPayload(buf.readBlockPos(), buf.readByte(),
                buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
