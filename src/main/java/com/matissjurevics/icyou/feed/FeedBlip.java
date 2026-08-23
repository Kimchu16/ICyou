package com.matissjurevics.icyou.feed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.PacketCodec;

/**
 * A single moving dot on a CCTV feed.
 *
 * @param u horizontal position on the panel, 0..1 (left to right)
 * @param v vertical position on the panel, 0..1 (top to bottom)
 * @param kind 0 = player, 1 = monster, 2 = other living entity
 */
public record FeedBlip(float u, float v, int kind) {

    public static final int KIND_PLAYER = 0;
    public static final int KIND_MONSTER = 1;
    public static final int KIND_OTHER = 2;

    /** NBT-friendly codec (useful later for saving snapshots / datagen). */
    public static final Codec<FeedBlip> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("u").forGetter(FeedBlip::u),
                    Codec.FLOAT.fieldOf("v").forGetter(FeedBlip::v),
                    Codec.INT.fieldOf("kind").forGetter(FeedBlip::kind))
                    .apply(instance, FeedBlip::new));
}
