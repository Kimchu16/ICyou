package com.matissjurevics.icyou.render.scene;

import java.util.Objects;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Logical-server to render-agent snapshot transfer message. */
public record SceneSnapshotS2CPayload(SceneSnapshotProtocol.Message message)
        implements CustomPayload {

    public static final CustomPayload.Id<SceneSnapshotS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ICyouMod.MOD_ID, "scene_snapshot"));
    public static final PacketCodec<RegistryByteBuf, SceneSnapshotS2CPayload> CODEC =
            PacketCodec.of(SceneSnapshotS2CPayload::write, SceneSnapshotS2CPayload::read);

    public SceneSnapshotS2CPayload {
        Objects.requireNonNull(message, "message");
    }

    private static void write(SceneSnapshotS2CPayload payload, RegistryByteBuf buffer) {
        SceneSnapshotProtocol.write(payload.message(), buffer);
    }

    private static SceneSnapshotS2CPayload read(RegistryByteBuf buffer) {
        return new SceneSnapshotS2CPayload(SceneSnapshotProtocol.read(buffer));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
