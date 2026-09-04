package com.matissjurevics.icyou.render.webrtc;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WebRtcOfferS2CPayload(WebRtcSignalingProtocol.Offer offer)
        implements CustomPayload {
    public WebRtcOfferS2CPayload {
        Objects.requireNonNull(offer, "offer");
    }
    public static final Id<WebRtcOfferS2CPayload> ID = new Id<>(
            Identifier.of(ICyouMod.MOD_ID, "webrtc_offer"));
    public static final PacketCodec<RegistryByteBuf, WebRtcOfferS2CPayload> CODEC =
            PacketCodec.of((value, buffer) ->
                    WebRtcSignalingProtocol.writeOffer(value.offer(), buffer),
                    buffer -> new WebRtcOfferS2CPayload(
                            WebRtcSignalingProtocol.readOffer(buffer)));

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
