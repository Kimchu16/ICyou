package com.matissjurevics.icyou.network;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.device.TerminalRef;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: a mutation performed in the terminal GUI.
 *
 * @param action 0 = assign camera to screen, 1 = rename, 2 = remove
 * @param targetType 0 = camera, 1 = screen, 2 = wireless
 * @param id target device id
 * @param auxId assign: camera id; unused otherwise
 * @param name rename: new name
 */
public record DeviceActionC2SPayload(TerminalRef terminal, byte action, byte targetType,
                                     UUID id, Optional<UUID> auxId, String name)
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
        TerminalRef.PACKET_CODEC.encode(buf, p.terminal());
        buf.writeByte(p.action());
        buf.writeByte(p.targetType());
        buf.writeUuid(p.id());
        buf.writeBoolean(p.auxId().isPresent());
        p.auxId().ifPresent(buf::writeUuid);
        buf.writeString(p.name());
    }

    private static DeviceActionC2SPayload read(RegistryByteBuf buf) {
        TerminalRef terminal = TerminalRef.PACKET_CODEC.decode(buf);
        byte action = buf.readByte();
        byte targetType = buf.readByte();
        UUID id = buf.readUuid();
        Optional<UUID> auxId = buf.readBoolean()
                ? Optional.of(buf.readUuid()) : Optional.empty();
        return new DeviceActionC2SPayload(terminal, action, targetType, id, auxId,
                buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
