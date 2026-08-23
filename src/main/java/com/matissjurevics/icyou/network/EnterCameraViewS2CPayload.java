package com.matissjurevics.icyou.network;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server → client: tells the client to detach its view onto this security
 * camera (right-clicked a bound screen).
 *
 * @param camPos the camera block to view from
 * @param facingId {@link net.minecraft.util.math.Direction#getId()} of the camera's facing
 */
public record EnterCameraViewS2CPayload(BlockPos camPos, int facingId)
        implements CustomPayload {

    public static final CustomPayload.Id<EnterCameraViewS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "enter_camera_view"));

    public static final PacketCodec<RegistryByteBuf, EnterCameraViewS2CPayload> CODEC =
            PacketCodec.of(EnterCameraViewS2CPayload::write, EnterCameraViewS2CPayload::read);

    private static void write(EnterCameraViewS2CPayload payload, RegistryByteBuf buf) {
        buf.writeBlockPos(payload.camPos());
        buf.writeByte(payload.facingId());
    }

    private static EnterCameraViewS2CPayload read(RegistryByteBuf buf) {
        return new EnterCameraViewS2CPayload(buf.readBlockPos(), buf.readByte());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
