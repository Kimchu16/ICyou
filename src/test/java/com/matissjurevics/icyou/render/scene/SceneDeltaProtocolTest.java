package com.matissjurevics.icyou.render.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.matissjurevics.icyou.render.scene.SceneDeltaProtocol.Delta;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;

class SceneDeltaProtocolTest {
    @Test
    void roundTripsAnOrderedBoundedDelta() {
        Delta source = new Delta(UUID.randomUUID(), 2, 7, 3, 100, 200,
                0.25f, 0.75f, new byte[] {1, 2, 3});
        assertEquals(source, roundTrip(source));
    }

    @Test
    void acceptsMetadataOnlyUpdatesAndCopiesPacketBytes() {
        byte[] bytes = new byte[] {4, 5};
        Delta delta = new Delta(UUID.randomUUID(), 0, 0, 1, 10, 20,
                0, 0, bytes);
        bytes[0] = 9;
        assertArrayEquals(new byte[] {4, 5}, delta.encodedPackets());
        assertNotSame(delta.encodedPackets(), delta.encodedPackets());
        assertEquals(0, roundTrip(new Delta(UUID.randomUUID(), 0, 0, 1,
                0, 0, 0, 0, new byte[0])).encodedPackets().length);
    }

    @Test
    void rejectsInvalidSequencesWeatherSizeAndVersions() {
        UUID jobId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Delta(jobId, 0, 0,
                0, 0, 0, 0, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new Delta(jobId, 0, 0,
                1, 0, 0, Float.NaN, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new Delta(jobId, 0, 0,
                1, 0, 0, 0, 0, new byte[SceneDeltaProtocol.MAX_DELTA_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> decode(buffer ->
                buffer.writeVarInt(SceneDeltaProtocol.VERSION + 1)));
    }

    private static Delta roundTrip(Delta delta) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(),
                DynamicRegistryManager.EMPTY);
        try {
            SceneDeltaS2CPayload.CODEC.encode(buffer, new SceneDeltaS2CPayload(delta));
            return SceneDeltaS2CPayload.CODEC.decode(buffer).delta();
        } finally {
            buffer.release();
        }
    }

    private static void decode(java.util.function.Consumer<RegistryByteBuf> writer) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(),
                DynamicRegistryManager.EMPTY);
        try {
            writer.accept(buffer);
            SceneDeltaProtocol.read(buffer);
        } finally {
            buffer.release();
        }
    }
}
