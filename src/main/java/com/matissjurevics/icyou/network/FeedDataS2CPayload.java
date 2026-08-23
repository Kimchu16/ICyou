package com.matissjurevics.icyou.network;

import java.util.ArrayList;
import java.util.List;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.feed.FeedBlip;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server → client: latest stylized-feed snapshot for one screen block.
 * Blip coordinates are panel-space (0..1) computed server-side from the
 * linked camera's view cone.
 */
public record FeedDataS2CPayload(BlockPos screenPos, List<FeedBlip> blips)
        implements CustomPayload {

    public static final CustomPayload.Id<FeedDataS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "feed_data"));

    public static final PacketCodec<RegistryByteBuf, FeedDataS2CPayload> CODEC =
            PacketCodec.of(FeedDataS2CPayload::write, FeedDataS2CPayload::read);

    private static void write(FeedDataS2CPayload payload, RegistryByteBuf buf) {
        buf.writeBlockPos(payload.screenPos());
        buf.writeVarInt(payload.blips().size());
        for (FeedBlip blip : payload.blips()) {
            buf.writeFloat(blip.u());
            buf.writeFloat(blip.v());
            buf.writeByte(blip.kind());
        }
    }

    private static FeedDataS2CPayload read(RegistryByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<FeedBlip> blips = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blips.add(new FeedBlip(buf.readFloat(), buf.readFloat(), buf.readByte()));
        }
        return new FeedDataS2CPayload(pos, blips);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
