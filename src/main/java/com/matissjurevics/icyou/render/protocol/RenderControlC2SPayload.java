package com.matissjurevics.icyou.render.protocol;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Render-agent to logical-server control message. */
public record RenderControlC2SPayload(RenderProtocol.ClientMessage message)
        implements CustomPayload {

    public static final CustomPayload.Id<RenderControlC2SPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "render_control_c2s"));
    public static final PacketCodec<RegistryByteBuf, RenderControlC2SPayload> CODEC =
            PacketCodec.of(RenderControlC2SPayload::write, RenderControlC2SPayload::read);

    public RenderControlC2SPayload {
        Objects.requireNonNull(message, "message");
    }

    private static void write(RenderControlC2SPayload payload, RegistryByteBuf buffer) {
        RenderProtocol.writeClient(payload.message(), buffer);
    }

    private static RenderControlC2SPayload read(RegistryByteBuf buffer) {
        return new RenderControlC2SPayload(RenderProtocol.readClient(buffer));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
