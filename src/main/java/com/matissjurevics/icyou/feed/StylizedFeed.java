package com.matissjurevics.icyou.feed;

import com.matissjurevics.icyou.ICyouMod;
import net.minecraft.util.Identifier;

/**
 * Cheap placeholder feed: CCTV-style imagery described by compact data
 * (static noise seed, entity blips, timestamp) rather than real pixels.
 * Intended to be replaced by a render-to-texture implementation later.
 */
public final class StylizedFeed implements CameraFeed {

    public static final StylizedFeed INSTANCE = new StylizedFeed();

    private long frame;

    private StylizedFeed() {}

    @Override
    public Identifier id() {
        return Identifier.of(ICyouMod.MOD_ID, "stylized");
    }

    @Override
    public void tick() {
        frame++;
    }

    /** Monotonically increasing frame counter; client renderers animate static from this. */
    public long frame() {
        return frame;
    }
}
