package com.matissjurevics.icyou.render.video;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Authenticated render-agent JPEG frame sent to the logical server. */
public record VideoFrameC2SPayload(VideoFrameProtocol.Frame frame)
        implements CustomPayload {

    public static final CustomPayload.Id<VideoFrameC2SPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "video_frame"));
    public static final PacketCodec<RegistryByteBuf, VideoFrameC2SPayload> CODEC =
            PacketCodec.of(VideoFrameC2SPayload::write, VideoFrameC2SPayload::read);

    public VideoFrameC2SPayload {
        Objects.requireNonNull(frame, "frame");
    }

    private static void write(VideoFrameC2SPayload payload, RegistryByteBuf buffer) {
        VideoFrameProtocol.write(payload.frame(), buffer);
    }

    private static VideoFrameC2SPayload read(RegistryByteBuf buffer) {
        return new VideoFrameC2SPayload(VideoFrameProtocol.read(buffer));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
