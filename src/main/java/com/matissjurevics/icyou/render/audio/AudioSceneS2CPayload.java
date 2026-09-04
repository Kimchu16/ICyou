package com.matissjurevics.icyou.render.audio;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** One ordered batch of camera-local vanilla sound events. */
public record AudioSceneS2CPayload(AudioSceneProtocol.Batch batch)
        implements CustomPayload {

    public static final CustomPayload.Id<AudioSceneS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "audio_scene"));
    public static final PacketCodec<RegistryByteBuf, AudioSceneS2CPayload> CODEC =
            PacketCodec.of(AudioSceneS2CPayload::write, AudioSceneS2CPayload::read);

    public AudioSceneS2CPayload {
        Objects.requireNonNull(batch, "batch");
    }

    private static void write(AudioSceneS2CPayload payload, RegistryByteBuf buffer) {
        AudioSceneProtocol.write(payload.batch(), buffer);
    }

    private static AudioSceneS2CPayload read(RegistryByteBuf buffer) {
        return new AudioSceneS2CPayload(AudioSceneProtocol.read(buffer));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
