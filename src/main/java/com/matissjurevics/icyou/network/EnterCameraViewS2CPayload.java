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
 * Server → client: detach the player's view onto a security camera
 * (portable screen use). Carries one or more cameras; the client shows the
 * first unless told otherwise.
 */
public record EnterCameraViewS2CPayload(List<CamRef> cameras)
        implements CustomPayload {

    /** A camera to view: its position plus facing id ({@link net.minecraft.util.math.Direction#getId()}). */
    public record CamRef(BlockPos pos, int facingId) {}

    public static final CustomPayload.Id<EnterCameraViewS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "enter_camera_view"));

    public static final PacketCodec<RegistryByteBuf, EnterCameraViewS2CPayload> CODEC =
            PacketCodec.of(EnterCameraViewS2CPayload::write, EnterCameraViewS2CPayload::read);

    private static void write(EnterCameraViewS2CPayload payload, RegistryByteBuf buf) {
        buf.writeVarInt(payload.cameras().size());
        for (CamRef ref : payload.cameras()) {
            buf.writeBlockPos(ref.pos());
            buf.writeByte(ref.facingId());
        }
    }

    private static EnterCameraViewS2CPayload read(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<CamRef> cameras = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cameras.add(new CamRef(buf.readBlockPos(), buf.readByte()));
        }
        return new EnterCameraViewS2CPayload(cameras);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
