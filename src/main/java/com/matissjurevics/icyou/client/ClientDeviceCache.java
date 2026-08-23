package com.matissjurevics.icyou.client;

import com.matissjurevics.icyou.network.DeviceSnapshotS2CPayload;

/** Latest device snapshot received from a terminal (shared by GUI + HUD). */
public final class ClientDeviceCache {

    private ClientDeviceCache() {}

    private static volatile DeviceSnapshotS2CPayload current;

    public static void update(DeviceSnapshotS2CPayload payload) {
        current = payload;
    }

    public static DeviceSnapshotS2CPayload get() {
        return current;
    }
}
