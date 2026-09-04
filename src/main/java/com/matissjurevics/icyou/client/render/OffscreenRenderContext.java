package com.matissjurevics.icyou.client.render;

import java.util.Objects;

import net.minecraft.client.gl.Framebuffer;

/** Routes nested vanilla render passes into one temporary offscreen target. */
public final class OffscreenRenderContext {

    public static final class Scope implements AutoCloseable {
        private final Framebuffer ownedTarget;
        private boolean closed;

        private Scope(Framebuffer ownedTarget) {
            this.ownedTarget = ownedTarget;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (target != ownedTarget) {
                    throw new IllegalStateException("Offscreen render scope changed unexpectedly");
                }
                target = null;
            }
        }
    }

    private static Framebuffer target;

    private OffscreenRenderContext() {
    }

    public static Scope enter(Framebuffer framebuffer) {
        Objects.requireNonNull(framebuffer, "framebuffer");
        if (target != null) {
            throw new IllegalStateException("Nested offscreen rendering is not supported");
        }
        target = framebuffer;
        return new Scope(framebuffer);
    }

    public static Framebuffer target() {
        return target;
    }

    public static boolean active() {
        return target != null;
    }
}
