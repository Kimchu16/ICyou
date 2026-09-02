package com.matissjurevics.icyou.device;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Stable camera identity plus its current location. */
public record CameraRef(UUID deviceId, RegistryKey<World> dimension, BlockPos position)
        implements DeviceRef {

    public static final PacketCodec<PacketByteBuf, CameraRef> PACKET_CODEC =
            PacketCodec.of(CameraRef::writePacket, CameraRef::readPacket);

    public CameraRef {
        deviceId = Objects.requireNonNull(deviceId, "deviceId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").toImmutable();
    }

    public NbtCompound toNbt() {
        return DeviceRefSerialization.toNbt(this);
    }

    public static CameraRef fromNbt(NbtCompound nbt) {
        var fields = DeviceRefSerialization.fromNbt(nbt);
        return new CameraRef(fields.deviceId(), fields.dimension(), fields.position());
    }

    private static void writePacket(CameraRef ref, PacketByteBuf buf) {
        DeviceRefSerialization.writePacket(ref, buf);
    }

    private static CameraRef readPacket(PacketByteBuf buf) {
        var fields = DeviceRefSerialization.readPacket(buf);
        return new CameraRef(fields.deviceId(), fields.dimension(), fields.position());
    }
}
