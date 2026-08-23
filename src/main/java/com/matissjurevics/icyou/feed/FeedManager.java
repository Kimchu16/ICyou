package com.matissjurevics.icyou.feed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;

/**
 * Central registry and ticker for all {@link CameraFeed} implementations.
 *
 * <p>Also owns the tick budget policy: every registered feed ticks here,
 * and future work (e.g. throttling expensive render-to-texture feeds to a
 * fixed FPS) belongs in this class rather than in feed implementations.</p>
 */
public final class FeedManager {

    private static final Map<Identifier, CameraFeed> FEEDS = new LinkedHashMap<>();

    private FeedManager() {}

    public static void register(CameraFeed feed) {
        FEEDS.put(feed.id(), feed);
    }

    public static Optional<CameraFeed> get(Identifier id) {
        return Optional.ofNullable(FEEDS.get(id));
    }

    /** Called by the mod entrypoint. */
    public static void init() {
        register(StylizedFeed.INSTANCE);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (CameraFeed feed : FEEDS.values()) {
                feed.tick();
            }
        });
    }
}
