package com.matissjurevics.icyou.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.ScreenRef;
import com.matissjurevics.icyou.device.TerminalRef;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: full device snapshot of one terminal (cameras, screens,
 * wireless screens). {@code openGui} instructs the client to open the
 * terminal management screen; otherwise it refreshes the cache / HUD.
 */
public record DeviceSnapshotS2CPayload(boolean openGui, TerminalRef terminal, String slug,
                                       List<Cam> cameras, List<Scr> screens,
                                       List<Wrl> wireless)
        implements CustomPayload {

    public record Cam(CameraRef ref, String name, int facingId, boolean online) {}
    public record Scr(ScreenRef ref, String name, Optional<UUID> cameraId,
                      String camName, boolean online) {}
    public record Wrl(UUID id, String name) {}

    public static final CustomPayload.Id<DeviceSnapshotS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "device_snapshot"));

    public static final PacketCodec<RegistryByteBuf, DeviceSnapshotS2CPayload> CODEC =
            PacketCodec.of(DeviceSnapshotS2CPayload::write, DeviceSnapshotS2CPayload::read);

    private static void write(DeviceSnapshotS2CPayload p, RegistryByteBuf buf) {
        buf.writeBoolean(p.openGui());
        TerminalRef.PACKET_CODEC.encode(buf, p.terminal());
        buf.writeString(p.slug());
        buf.writeVarInt(p.cameras().size());
        for (Cam c : p.cameras()) {
            CameraRef.PACKET_CODEC.encode(buf, c.ref());
            buf.writeString(c.name());
            buf.writeVarInt(c.facingId());
            buf.writeBoolean(c.online());
        }
        buf.writeVarInt(p.screens().size());
        for (Scr s : p.screens()) {
            ScreenRef.PACKET_CODEC.encode(buf, s.ref());
            buf.writeString(s.name());
            buf.writeBoolean(s.cameraId().isPresent());
            s.cameraId().ifPresent(buf::writeUuid);
            buf.writeString(s.camName());
            buf.writeBoolean(s.online());
        }
        buf.writeVarInt(p.wireless().size());
        for (Wrl w : p.wireless()) {
            buf.writeUuid(w.id());
            buf.writeString(w.name());
        }
    }

    private static DeviceSnapshotS2CPayload read(RegistryByteBuf buf) {
        boolean openGui = buf.readBoolean();
        TerminalRef terminal = TerminalRef.PACKET_CODEC.decode(buf);
        String slug = buf.readString();
        int camCount = buf.readVarInt();
        List<Cam> cameras = new ArrayList<>(camCount);
        for (int i = 0; i < camCount; i++) {
            cameras.add(new Cam(CameraRef.PACKET_CODEC.decode(buf), buf.readString(),
                    buf.readVarInt(), buf.readBoolean()));
        }
        int scrCount = buf.readVarInt();
        List<Scr> screens = new ArrayList<>(scrCount);
        for (int i = 0; i < scrCount; i++) {
            ScreenRef ref = ScreenRef.PACKET_CODEC.decode(buf);
            String name = buf.readString();
            Optional<UUID> cameraId = buf.readBoolean()
                    ? Optional.of(buf.readUuid()) : Optional.empty();
            screens.add(new Scr(ref, name, cameraId, buf.readString(), buf.readBoolean()));
        }
        int wrlCount = buf.readVarInt();
        List<Wrl> wireless = new ArrayList<>(wrlCount);
        for (int i = 0; i < wrlCount; i++) {
            wireless.add(new Wrl(buf.readUuid(), buf.readString()));
        }
        return new DeviceSnapshotS2CPayload(openGui, terminal, slug, cameras, screens, wireless);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
