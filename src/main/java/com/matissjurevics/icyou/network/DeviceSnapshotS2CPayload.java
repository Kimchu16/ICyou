package com.matissjurevics.icyou.network;

import java.util.ArrayList;
import java.util.List;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server → client: full device snapshot of one terminal (cameras, screens,
 * wireless screens). {@code openGui} instructs the client to open the
 * terminal management screen; otherwise it refreshes the cache / HUD.
 */
public record DeviceSnapshotS2CPayload(boolean openGui, BlockPos terminal,
                                       List<Cam> cameras, List<Scr> screens,
                                       List<Wrl> wireless)
        implements CustomPayload {

    public record Cam(int id, String name, BlockPos pos, int facingId, boolean online) {}
    public record Scr(int id, String name, int camId, String camName, boolean online) {}
    public record Wrl(int id, String name) {}

    public static final CustomPayload.Id<DeviceSnapshotS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "device_snapshot"));

    public static final PacketCodec<RegistryByteBuf, DeviceSnapshotS2CPayload> CODEC =
            PacketCodec.of(DeviceSnapshotS2CPayload::write, DeviceSnapshotS2CPayload::read);

    private static void write(DeviceSnapshotS2CPayload p, RegistryByteBuf buf) {
        buf.writeBoolean(p.openGui());
        buf.writeBlockPos(p.terminal());
        buf.writeVarInt(p.cameras().size());
        for (Cam c : p.cameras()) {
            buf.writeVarInt(c.id());
            buf.writeString(c.name());
            buf.writeBlockPos(c.pos());
            buf.writeVarInt(c.facingId());
            buf.writeBoolean(c.online());
        }
        buf.writeVarInt(p.screens().size());
        for (Scr s : p.screens()) {
            buf.writeVarInt(s.id());
            buf.writeString(s.name());
            buf.writeVarInt(s.camId());
            buf.writeString(s.camName());
            buf.writeBoolean(s.online());
        }
        buf.writeVarInt(p.wireless().size());
        for (Wrl w : p.wireless()) {
            buf.writeVarInt(w.id());
            buf.writeString(w.name());
        }
    }

    private static DeviceSnapshotS2CPayload read(RegistryByteBuf buf) {
        boolean openGui = buf.readBoolean();
        BlockPos terminal = buf.readBlockPos();
        int camCount = buf.readVarInt();
        List<Cam> cameras = new ArrayList<>(camCount);
        for (int i = 0; i < camCount; i++) {
            cameras.add(new Cam(buf.readVarInt(), buf.readString(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readBoolean()));
        }
        int scrCount = buf.readVarInt();
        List<Scr> screens = new ArrayList<>(scrCount);
        for (int i = 0; i < scrCount; i++) {
            screens.add(new Scr(buf.readVarInt(), buf.readString(), buf.readVarInt(),
                    buf.readString(), buf.readBoolean()));
        }
        int wrlCount = buf.readVarInt();
        List<Wrl> wireless = new ArrayList<>(wrlCount);
        for (int i = 0; i < wrlCount; i++) {
            wireless.add(new Wrl(buf.readVarInt(), buf.readString()));
        }
        return new DeviceSnapshotS2CPayload(openGui, terminal, cameras, screens, wireless);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
