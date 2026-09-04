package com.matissjurevics.icyou.render.webrtc;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WebRtcAnswerC2SPayload(WebRtcSignalingProtocol.Answer answer)
        implements CustomPayload {
    public WebRtcAnswerC2SPayload {
        Objects.requireNonNull(answer, "answer");
    }
    public static final Id<WebRtcAnswerC2SPayload> ID = new Id<>(
            Identifier.of(ICyouMod.MOD_ID, "webrtc_answer"));
    public static final PacketCodec<RegistryByteBuf, WebRtcAnswerC2SPayload> CODEC =
            PacketCodec.of((value, buffer) ->
                    WebRtcSignalingProtocol.writeAnswer(value.answer(), buffer),
                    buffer -> new WebRtcAnswerC2SPayload(
                            WebRtcSignalingProtocol.readAnswer(buffer)));

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
