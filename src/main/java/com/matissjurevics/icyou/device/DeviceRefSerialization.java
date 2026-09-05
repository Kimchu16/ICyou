package com.matissjurevics.icyou.device;

import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Shared wire/save layout for the three strongly typed device references. */
final class DeviceRefSerialization {

    private static final String VERSION_KEY = "version";
    private static final String ID_KEY = "id";
    private static final String DIMENSION_KEY = "dimension";
    private static final String POSITION_KEY = "position";

    private DeviceRefSerialization() {
    }

    static NbtCompound toNbt(DeviceRef ref) {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt(VERSION_KEY, CameraOverhaulContracts.SAVE_SCHEMA_VERSION);
        nbt.putUuid(ID_KEY, ref.deviceId());
        nbt.putString(DIMENSION_KEY, ref.dimension().getValue().toString());
        nbt.putLong(POSITION_KEY, ref.position().asLong());
        return nbt;
    }

    static Fields fromNbt(NbtCompound nbt) {
        Objects.requireNonNull(nbt, "nbt");
        requireVersion(nbt.getInt(VERSION_KEY), CameraOverhaulContracts.SAVE_SCHEMA_VERSION,
                "save schema");
        if (!nbt.containsUuid(ID_KEY) || !nbt.contains(DIMENSION_KEY, NbtElement.STRING_TYPE)
                || !nbt.contains(POSITION_KEY, NbtElement.LONG_TYPE)) {
            throw new IllegalArgumentException("Device reference is missing required fields");
        }
        return new Fields(nbt.getUuid(ID_KEY), worldKey(Identifier.of(nbt.getString(DIMENSION_KEY))),
                BlockPos.fromLong(nbt.getLong(POSITION_KEY)));
    }

    static void writePacket(DeviceRef ref, PacketByteBuf buf) {
        buf.writeVarInt(CameraOverhaulContracts.DEVICE_NETWORK_PROTOCOL_VERSION);
        buf.writeUuid(ref.deviceId());
        buf.writeIdentifier(ref.dimension().getValue());
        buf.writeBlockPos(ref.position());
    }

    static Fields readPacket(PacketByteBuf buf) {
        requireVersion(buf.readVarInt(), CameraOverhaulContracts.DEVICE_NETWORK_PROTOCOL_VERSION,
                "network protocol");
        return new Fields(buf.readUuid(), worldKey(buf.readIdentifier()), buf.readBlockPos());
    }

    private static RegistryKey<World> worldKey(Identifier id) {
        return RegistryKey.of(RegistryKeys.WORLD, id);
    }

    private static void requireVersion(int actual, int expected, String kind) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    "Unsupported device reference " + kind + " version: " + actual);
        }
    }

    record Fields(UUID deviceId, RegistryKey<World> dimension, BlockPos position) {
    }
}
