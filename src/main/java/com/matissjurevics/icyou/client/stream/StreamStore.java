package com.matissjurevics.icyou.client.stream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/** Latest frame per stable camera UUID; thread-safe. */
public final class StreamStore {

    private StreamStore() {}

    private static final Map<UUID, StreamFrame> FRAMES = new ConcurrentHashMap<>();

    public static void put(UUID camKey, StreamFrame frame) {
        FRAMES.put(camKey, frame);
    }

    public static StreamFrame get(UUID camKey) {
        return FRAMES.get(camKey);
    }
}
