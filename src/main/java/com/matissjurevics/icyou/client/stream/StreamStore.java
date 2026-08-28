package com.matissjurevics.icyou.client.stream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Latest frame per camera (keyed by {@code cameraPos.asLong()}); thread-safe. */
public final class StreamStore {

    private StreamStore() {}

    private static final Map<Long, StreamFrame> FRAMES = new ConcurrentHashMap<>();

    public static void put(long camKey, StreamFrame frame) {
        FRAMES.put(camKey, frame);
    }

    public static StreamFrame get(long camKey) {
        return FRAMES.get(camKey);
    }
}
