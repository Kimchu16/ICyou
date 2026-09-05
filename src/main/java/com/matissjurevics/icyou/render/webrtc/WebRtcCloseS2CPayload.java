package com.matissjurevics.icyou.render.webrtc;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WebRtcCloseS2CPayload(WebRtcSignalingProtocol.Close close)
        implements CustomPayload {
    public WebRtcCloseS2CPayload {
        Objects.requireNonNull(close, "close");
    }
    public static final Id<WebRtcCloseS2CPayload> ID = new Id<>(
            Identifier.of(ICyouMod.MOD_ID, "webrtc_close"));
    public static final PacketCodec<RegistryByteBuf, WebRtcCloseS2CPayload> CODEC =
            PacketCodec.of((value, buffer) ->
                    WebRtcSignalingProtocol.writeClose(value.close(), buffer),
                    buffer -> new WebRtcCloseS2CPayload(
                            WebRtcSignalingProtocol.readClose(buffer)));

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
