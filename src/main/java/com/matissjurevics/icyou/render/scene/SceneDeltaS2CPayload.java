package com.matissjurevics.icyou.render.scene;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Logical-server to render-agent ordered scene delta. */
public record SceneDeltaS2CPayload(SceneDeltaProtocol.Delta delta)
        implements CustomPayload {

    public static final CustomPayload.Id<SceneDeltaS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "scene_delta"));
    public static final PacketCodec<RegistryByteBuf, SceneDeltaS2CPayload> CODEC =
            PacketCodec.of(SceneDeltaS2CPayload::write, SceneDeltaS2CPayload::read);

    public SceneDeltaS2CPayload {
        Objects.requireNonNull(delta, "delta");
    }

    private static void write(SceneDeltaS2CPayload payload, RegistryByteBuf buffer) {
        SceneDeltaProtocol.write(payload.delta(), buffer);
    }

    private static SceneDeltaS2CPayload read(RegistryByteBuf buffer) {
        return new SceneDeltaS2CPayload(SceneDeltaProtocol.read(buffer));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
