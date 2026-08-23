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
 * Server → client: latest stylized-feed snapshot for one screen block —
 * the blips, the camera facing (for the HUD label), and the channel position
 * within the paired terminal.
 */
public record FeedDataS2CPayload(BlockPos screenPos, int facingId, int index, int count,
                                 List<FeedBlip> blips)
        implements CustomPayload {

    public static final CustomPayload.Id<FeedDataS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "feed_data"));

    public static final PacketCodec<RegistryByteBuf, FeedDataS2CPayload> CODEC =
            PacketCodec.of(FeedDataS2CPayload::write, FeedDataS2CPayload::read);

    private static void write(FeedDataS2CPayload payload, RegistryByteBuf buf) {
        buf.writeBlockPos(payload.screenPos());
        buf.writeVarInt(payload.facingId());
        buf.writeVarInt(payload.index());
        buf.writeVarInt(payload.count());
        buf.writeVarInt(payload.blips().size());
        for (FeedBlip blip : payload.blips()) {
            buf.writeFloat(blip.u());
            buf.writeFloat(blip.v());
            buf.writeByte(blip.kind());
        }
    }

    private static FeedDataS2CPayload read(RegistryByteBuf buf) {
        BlockPos screenPos = buf.readBlockPos();
        int facingId = buf.readVarInt();
        int index = buf.readVarInt();
        int count = buf.readVarInt();
        int blipCount = buf.readVarInt();
        List<FeedBlip> blips = new ArrayList<>(blipCount);
        for (int i = 0; i < blipCount; i++) {
            blips.add(new FeedBlip(buf.readFloat(), buf.readFloat(), buf.readByte()));
        }
        return new FeedDataS2CPayload(screenPos, facingId, index, count, blips);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
