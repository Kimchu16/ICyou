package com.matissjurevics.icyou.render.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Batch;
import com.matissjurevics.icyou.render.audio.AudioSceneProtocol.Event;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

class AudioSceneProtocolTest {

    @Test
    void payloadRoundTripsEverySoundField() {
        Batch batch = new Batch(UUID.randomUUID(), 2, 3, 4, 5, true,
                List.of(event(1), event(2)));
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            AudioSceneS2CPayload.CODEC.encode(buffer, new AudioSceneS2CPayload(batch));
            assertEquals(batch, AudioSceneS2CPayload.CODEC.decode(buffer).batch());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsInvalidSequencesFieldsAndBatchSizes() {
        assertThrows(IllegalArgumentException.class, () -> new Batch(
                UUID.randomUUID(), 0, 0, 0, 0, false, List.of(event(1))));
        assertThrows(IllegalArgumentException.class, () -> new Batch(
                UUID.randomUUID(), 0, 0, 1, 0, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Event(
                Identifier.of("minecraft", "test"), SoundCategory.BLOCKS,
                Double.NaN, 0, 0, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Event(
                Identifier.of("minecraft", "test"), SoundCategory.BLOCKS,
                0, 0, 0, AudioSceneProtocol.MAX_VOLUME + 1, 1, 0));
    }

    @Test
    void rejectsUnknownWireVersionsAndCategories() {
        RegistryByteBuf version = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            version.writeVarInt(AudioSceneProtocol.VERSION + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> AudioSceneProtocol.read(version));
        } finally {
            version.release();
        }
        RegistryByteBuf category = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            category.writeVarInt(AudioSceneProtocol.VERSION);
            category.writeUuid(UUID.randomUUID());
            category.writeVarLong(0);
            category.writeVarLong(0);
            category.writeVarLong(1);
            category.writeLong(0);
            category.writeBoolean(false);
            category.writeVarInt(1);
            category.writeString("minecraft:test", AudioSceneProtocol.MAX_SOUND_ID_CHARS);
            category.writeByte(99);
            assertThrows(IllegalArgumentException.class,
                    () -> AudioSceneProtocol.read(category));
        } finally {
            category.release();
        }
    }

    private static Event event(int seed) {
        return new Event(Identifier.of("minecraft", "test_" + seed),
                SoundCategory.BLOCKS, 1.25, 2.5, 3.75, 1.0f, 0.75f, seed);
    }
}
