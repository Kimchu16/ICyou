package com.matissjurevics.icyou.feed;

import net.minecraft.util.Identifier;

/**
 * A source of visual data produced by a camera.
 *
 * <p>Implementations must stay side-neutral: rendering happens client-side
 * (see {@code client.render}), while this interface models the feed itself so
 * that consumers (terminal, screen) never depend on a concrete feed type.
 * This lets us swap {@link StylizedFeed} for a real render-to-texture feed
 * later without touching anything downstream.</p>
 */
public interface CameraFeed {

    /** Unique identifier of this feed type, e.g. {@code icyou:stylized}. */
    Identifier id();

    /** Called once per tick while the feed is active. */
    void tick();
}
