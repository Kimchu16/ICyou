package com.matissjurevics.icyou.device;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Stable in-world screen identity plus its current location. */
public record ScreenRef(UUID deviceId, RegistryKey<World> dimension, BlockPos position)
        implements DeviceRef {

    public static final PacketCodec<PacketByteBuf, ScreenRef> PACKET_CODEC =
            PacketCodec.of(ScreenRef::writePacket, ScreenRef::readPacket);

    public ScreenRef {
        deviceId = Objects.requireNonNull(deviceId, "deviceId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").toImmutable();
    }

    public NbtCompound toNbt() {
        return DeviceRefSerialization.toNbt(this);
    }

    public static ScreenRef fromNbt(NbtCompound nbt) {
        var fields = DeviceRefSerialization.fromNbt(nbt);
        return new ScreenRef(fields.deviceId(), fields.dimension(), fields.position());
    }

    private static void writePacket(ScreenRef ref, PacketByteBuf buf) {
        DeviceRefSerialization.writePacket(ref, buf);
    }

    private static ScreenRef readPacket(PacketByteBuf buf) {
        var fields = DeviceRefSerialization.readPacket(buf);
        return new ScreenRef(fields.deviceId(), fields.dimension(), fields.position());
    }
}
