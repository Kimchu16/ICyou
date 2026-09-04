package com.matissjurevics.icyou.render.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotBegin;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotPart;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class SceneSnapshotProtocolTest {

    private static final CameraRef CAMERA = new CameraRef(new UUID(0, 1), World.OVERWORLD,
            new BlockPos(4, 70, 8));

    @Test
    void fragmentsAndRoundTripsBoundedMessages() {
        byte[] source = new byte[SceneSnapshotProtocol.MAX_PART_BYTES + 17];
        Arrays.fill(source, (byte) 5);
        var transfer = SceneSnapshotProtocol.fragment(new UUID(0, 2), 3, 4, CAMERA,
                100, 200, 0.25f, 0.75f, source);

        SnapshotBegin begin = (SnapshotBegin) roundTrip(transfer.begin()).message();
        SnapshotPart first = (SnapshotPart) roundTrip(transfer.parts().get(0)).message();
        SnapshotPart second = (SnapshotPart) roundTrip(transfer.parts().get(1)).message();

        assertEquals(transfer.begin(), begin);
        assertEquals(SceneSnapshotProtocol.MAX_PART_BYTES, first.data().length);
        assertEquals(17, second.data().length);
        assertArrayEquals(SceneSnapshotProtocol.sha256(source), begin.sha256());
    }

    @Test
    void digestAndPartBytesAreDefensivelyCopied() {
        byte[] source = new byte[] {1, 2, 3};
        var transfer = SceneSnapshotProtocol.fragment(UUID.randomUUID(), 0, 0, CAMERA,
                0, 0, 0, 0, source);
        source[0] = 9;

        assertEquals(1, transfer.parts().get(0).data()[0]);
        assertNotSame(transfer.parts().get(0).data(), transfer.parts().get(0).data());
        assertNotSame(transfer.begin().sha256(), transfer.begin().sha256());
    }

    @Test
    void rejectsInvalidBoundsAndFutureProtocolInput() {
        byte[] digest = new byte[SceneSnapshotProtocol.DIGEST_BYTES];
        assertThrows(IllegalArgumentException.class, () -> new SnapshotBegin(
                UUID.randomUUID(), UUID.randomUUID(), -1, 0, CAMERA, 0, 0,
                0, 0, 1, 1, digest));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotBegin(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, CAMERA, 0, 0,
                Float.NaN, 0, 1, 1, digest));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotBegin(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, CAMERA, 0, 0,
                0, 0, SceneSnapshotProtocol.MAX_PART_BYTES + 1, 1, digest));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotPart(
                UUID.randomUUID(), 0, new byte[SceneSnapshotProtocol.MAX_PART_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> decode(buffer -> {
            buffer.writeVarInt(SceneSnapshotProtocol.VERSION + 1);
            buffer.writeByte(1);
        }));
        assertThrows(IllegalArgumentException.class, () -> decode(buffer -> {
            buffer.writeVarInt(SceneSnapshotProtocol.VERSION);
            buffer.writeByte(99);
        }));
    }

    @Test
    void packetStreamPreservesBoundariesAndRejectsMalformedData() {
        byte[] encoded = ScenePacketStream.encode(List.of(
                new byte[] {1, 2}, new byte[] {3}, new byte[] {4, 5, 6}));

        List<byte[]> decoded = ScenePacketStream.decode(encoded);

        assertArrayEquals(new byte[] {1, 2}, decoded.get(0));
        assertArrayEquals(new byte[] {3}, decoded.get(1));
        assertArrayEquals(new byte[] {4, 5, 6}, decoded.get(2));
        assertThrows(IllegalArgumentException.class,
                () -> ScenePacketStream.decode(new byte[] {0}));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ScenePacketStream.decode(trailing));
    }

    private static SceneSnapshotS2CPayload roundTrip(SceneSnapshotProtocol.Message message) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(),
                DynamicRegistryManager.EMPTY);
        try {
            SceneSnapshotS2CPayload.CODEC.encode(buffer, new SceneSnapshotS2CPayload(message));
            return SceneSnapshotS2CPayload.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void decode(java.util.function.Consumer<RegistryByteBuf> writer) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(),
                DynamicRegistryManager.EMPTY);
        try {
            writer.accept(buffer);
            SceneSnapshotProtocol.read(buffer);
        } finally {
            buffer.release();
        }
    }
}
