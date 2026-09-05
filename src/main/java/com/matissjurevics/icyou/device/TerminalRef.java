package com.matissjurevics.icyou.device;

import java.util.Objects;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Stable terminal identity plus its current location. */
public record TerminalRef(UUID deviceId, RegistryKey<World> dimension, BlockPos position)
        implements DeviceRef {

    public static final Codec<TerminalRef> CODEC = NbtCompound.CODEC.xmap(
            TerminalRef::fromNbt, TerminalRef::toNbt);
    public static final PacketCodec<PacketByteBuf, TerminalRef> PACKET_CODEC =
            PacketCodec.of(TerminalRef::writePacket, TerminalRef::readPacket);

    public TerminalRef {
        deviceId = Objects.requireNonNull(deviceId, "deviceId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").toImmutable();
    }

    public NbtCompound toNbt() {
        return DeviceRefSerialization.toNbt(this);
    }

    public static TerminalRef fromNbt(NbtCompound nbt) {
        var fields = DeviceRefSerialization.fromNbt(nbt);
        return new TerminalRef(fields.deviceId(), fields.dimension(), fields.position());
    }

    private static void writePacket(TerminalRef ref, PacketByteBuf buf) {
        DeviceRefSerialization.writePacket(ref, buf);
    }

    private static TerminalRef readPacket(PacketByteBuf buf) {
        var fields = DeviceRefSerialization.readPacket(buf);
        return new TerminalRef(fields.deviceId(), fields.dimension(), fields.position());
    }
}
