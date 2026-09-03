package com.matissjurevics.icyou.render.protocol;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Logical-server to render-agent control message. */
public record RenderControlS2CPayload(RenderProtocol.ServerMessage message)
        implements CustomPayload {

    public static final CustomPayload.Id<RenderControlS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "render_control_s2c"));
    public static final PacketCodec<RegistryByteBuf, RenderControlS2CPayload> CODEC =
            PacketCodec.of(RenderControlS2CPayload::write, RenderControlS2CPayload::read);

    public RenderControlS2CPayload {
        Objects.requireNonNull(message, "message");
    }

    private static void write(RenderControlS2CPayload payload, RegistryByteBuf buffer) {
        RenderProtocol.writeServer(payload.message(), buffer);
    }

    private static RenderControlS2CPayload read(RegistryByteBuf buffer) {
        return new RenderControlS2CPayload(RenderProtocol.readServer(buffer));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
