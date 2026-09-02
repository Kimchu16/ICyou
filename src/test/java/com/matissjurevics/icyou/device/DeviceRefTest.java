package com.matissjurevics.icyou.device;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class DeviceRefTest {

    private static final UUID ID = UUID.fromString("85bd2f61-8fc8-432f-9630-b86a7d01d5ee");
    private static final RegistryKey<World> DIMENSION = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of("icyou", "reference_test"));
    private static final BlockPos POSITION = new BlockPos(123, -17, -456);

    @Test
    void saveFormatRoundTripsEveryReferenceType() {
        CameraRef camera = new CameraRef(ID, DIMENSION, POSITION);
        TerminalRef terminal = new TerminalRef(ID, DIMENSION, POSITION);
        ScreenRef screen = new ScreenRef(ID, DIMENSION, POSITION);

        assertAll(
                () -> assertEquals(camera, CameraRef.fromNbt(camera.toNbt())),
                () -> assertEquals(terminal, TerminalRef.fromNbt(terminal.toNbt())),
                () -> assertEquals(screen, ScreenRef.fromNbt(screen.toNbt())));
    }

    @Test
    void packetFormatRoundTripsEveryReferenceType() {
        CameraRef camera = new CameraRef(ID, DIMENSION, POSITION);
        TerminalRef terminal = new TerminalRef(ID, DIMENSION, POSITION);
        ScreenRef screen = new ScreenRef(ID, DIMENSION, POSITION);

        assertAll(
                () -> assertEquals(camera, packetRoundTrip(CameraRef.PACKET_CODEC, camera)),
                () -> assertEquals(terminal, packetRoundTrip(TerminalRef.PACKET_CODEC, terminal)),
                () -> assertEquals(screen, packetRoundTrip(ScreenRef.PACKET_CODEC, screen)));
    }

    @Test
    void constructorCopiesMutablePositionAndRejectsNulls() {
        BlockPos.Mutable mutable = new BlockPos.Mutable(1, 2, 3);
        CameraRef ref = new CameraRef(ID, DIMENSION, mutable);
        mutable.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), ref.position());
        assertNotSame(mutable, ref.position());
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new CameraRef(null, DIMENSION, POSITION)),
                () -> assertThrows(NullPointerException.class,
                        () -> new TerminalRef(ID, null, POSITION)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ScreenRef(ID, DIMENSION, null)));
    }

    @Test
    void dimensionParticipatesInValueSemantics() {
        RegistryKey<World> otherDimension = RegistryKey.of(
                RegistryKeys.WORLD, Identifier.of("icyou", "other_dimension"));

        assertNotEquals(new CameraRef(ID, DIMENSION, POSITION),
                new CameraRef(ID, otherDimension, POSITION));
    }

    @Test
    void unknownSaveAndNetworkVersionsAreRejected() {
        NbtCompound nbt = new CameraRef(ID, DIMENSION, POSITION).toNbt();
        nbt.putInt("version", 2);
        assertThrows(IllegalArgumentException.class, () -> CameraRef.fromNbt(nbt));

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(2);
            assertThrows(IllegalArgumentException.class, () -> CameraRef.PACKET_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    private static <T> T packetRoundTrip(PacketCodec<PacketByteBuf, T> codec, T value) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            codec.encode(buf, value);
            return codec.decode(buf);
        } finally {
            buf.release();
        }
    }
}
