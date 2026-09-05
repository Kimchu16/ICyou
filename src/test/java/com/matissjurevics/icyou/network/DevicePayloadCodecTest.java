package com.matissjurevics.icyou.network;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.ScreenRef;
import com.matissjurevics.icyou.device.TerminalRef;
import com.matissjurevics.icyou.feed.FeedBlip;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class DevicePayloadCodecTest {

    private static final RegistryKey<World> DIMENSION = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of("icyou", "payload_test"));
    private static final TerminalRef TERMINAL = new TerminalRef(uuid(1), DIMENSION,
            new BlockPos(1, 64, 1));
    private static final CameraRef CAMERA = new CameraRef(uuid(2), DIMENSION,
            new BlockPos(2, 64, 2));
    private static final ScreenRef SCREEN = new ScreenRef(uuid(3), DIMENSION,
            new BlockPos(3, 64, 3));

    @Test
    void allDevicePayloadsRoundTripTypedReferences() {
        DeviceSubscribeC2SPayload subscribe = new DeviceSubscribeC2SPayload(TERMINAL, true);
        DeviceActionC2SPayload action = new DeviceActionC2SPayload(TERMINAL,
                DeviceActionC2SPayload.ACTION_ASSIGN, DeviceActionC2SPayload.TYPE_SCREEN,
                SCREEN.deviceId(), Optional.of(CAMERA.deviceId()), "");
        DeviceSnapshotS2CPayload snapshot = new DeviceSnapshotS2CPayload(true, TERMINAL,
                "test-slug", List.of(new DeviceSnapshotS2CPayload.Cam(
                CAMERA, "Camera", 2, true)), List.of(new DeviceSnapshotS2CPayload.Scr(
                SCREEN, "Screen", Optional.of(CAMERA.deviceId()), "Camera", true)), List.of());
        FeedDataS2CPayload feed = new FeedDataS2CPayload(SCREEN, CAMERA, 2, 1, 1,
                List.of(new FeedBlip(0.25f, 0.75f, FeedBlip.KIND_OTHER)));
        EnterCameraViewS2CPayload view = new EnterCameraViewS2CPayload(
                List.of(new EnterCameraViewS2CPayload.CamRef(CAMERA, 2)));

        assertAll(
                () -> assertEquals(subscribe, roundTrip(DeviceSubscribeC2SPayload.CODEC, subscribe)),
                () -> assertEquals(action, roundTrip(DeviceActionC2SPayload.CODEC, action)),
                () -> assertEquals(snapshot, roundTrip(DeviceSnapshotS2CPayload.CODEC, snapshot)),
                () -> assertEquals(feed, roundTrip(FeedDataS2CPayload.CODEC, feed)),
                () -> assertEquals(view, roundTrip(EnterCameraViewS2CPayload.CODEC, view)));
    }

    private static <T> T roundTrip(PacketCodec<RegistryByteBuf, T> codec, T value) {
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }
}
