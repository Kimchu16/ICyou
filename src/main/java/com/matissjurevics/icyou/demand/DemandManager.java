package com.matissjurevics.icyou.demand;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.FeedLifecycleState;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

/** Combines every demand source and owns each feed's control-plane lifetime. */
public final class DemandManager {

    public static final Duration RETENTION_PERIOD = Duration.ofSeconds(
            CameraOverhaulContracts.RESOURCE_GRACE_SECONDS);

    public enum ServerMode {
        INTEGRATED,
        LAN,
        DEDICATED
    }

    public record ActivationContext(ServerMode mode, boolean running, boolean paused,
                                    int genuinePlayers, int authorizedRenderAgents) {
        public ActivationContext {
            Objects.requireNonNull(mode, "mode");
            if (genuinePlayers < 0 || authorizedRenderAgents < 0) {
                throw new IllegalArgumentException("Presence counts cannot be negative");
            }
        }

        public boolean permitsActivation() {
            if (!running) {
                return false;
            }
            return switch (mode) {
                case INTEGRATED -> !paused;
                case LAN -> genuinePlayers > 0;
                case DEDICATED -> genuinePlayers > 0 || authorizedRenderAgents > 0;
            };
        }
    }

    public record Demand(UUID cameraId, int webViewers, int screens,
                         FeedLifecycleState lifecycle) {
        public Demand {
            Objects.requireNonNull(cameraId, "cameraId");
            Objects.requireNonNull(lifecycle, "lifecycle");
        }

        public boolean demanded() {
            return webViewers > 0 || screens > 0;
        }
    }

    private static final class FeedState {
        private int webViewers;
        private int screens;
        private FeedLifecycleState lifecycle = FeedLifecycleState.INACTIVE;
        private Instant retainingSince;
    }

    private final Map<UUID, FeedState> feeds = new LinkedHashMap<>();

    public synchronized void reconcile(Map<UUID, Integer> webViewers,
                                       Map<UUID, Integer> screens,
                                       ActivationContext context, Instant now) {
        Objects.requireNonNull(webViewers, "webViewers");
        Objects.requireNonNull(screens, "screens");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(now, "now");

        Set<UUID> cameraIds = new LinkedHashSet<>(feeds.keySet());
        cameraIds.addAll(webViewers.keySet());
        cameraIds.addAll(screens.keySet());
        for (UUID cameraId : cameraIds) {
            FeedState feed = feeds.computeIfAbsent(cameraId, ignored -> new FeedState());
            feed.webViewers = count(webViewers, cameraId);
            feed.screens = count(screens, cameraId);
            if (feed.webViewers > 0 || feed.screens > 0) {
                feed.retainingSince = null;
                if (!context.permitsActivation()) {
                    moveToUnavailable(feed);
                } else if (feed.lifecycle == FeedLifecycleState.INACTIVE
                        || feed.lifecycle == FeedLifecycleState.RETAINING
                        || feed.lifecycle == FeedLifecycleState.UNAVAILABLE) {
                    transition(feed, FeedLifecycleState.ACTIVATING);
                }
            } else if (feed.lifecycle != FeedLifecycleState.INACTIVE) {
                if (feed.lifecycle != FeedLifecycleState.RETAINING) {
                    transition(feed, FeedLifecycleState.RETAINING);
                    feed.retainingSince = now;
                } else if (!now.isBefore(feed.retainingSince.plus(RETENTION_PERIOD))) {
                    transition(feed, FeedLifecycleState.INACTIVE);
                    feed.retainingSince = null;
                }
            }
        }
    }

    public synchronized void markAvailable(UUID cameraId) {
        FeedState feed = requireDemanded(cameraId);
        transition(feed, FeedLifecycleState.AVAILABLE);
    }

    public synchronized void markUnavailable(UUID cameraId) {
        FeedState feed = requireDemanded(cameraId);
        moveToUnavailable(feed);
    }

    public synchronized FeedLifecycleState lifecycle(UUID cameraId) {
        FeedState feed = feeds.get(cameraId);
        return feed == null ? FeedLifecycleState.INACTIVE : feed.lifecycle;
    }

    public synchronized Optional<Demand> demand(UUID cameraId) {
        FeedState feed = feeds.get(cameraId);
        return feed == null ? Optional.empty() : Optional.of(snapshot(cameraId, feed));
    }

    public synchronized Map<UUID, Demand> demands() {
        Map<UUID, Demand> result = new LinkedHashMap<>();
        feeds.forEach((cameraId, feed) -> result.put(cameraId, snapshot(cameraId, feed)));
        return Map.copyOf(result);
    }

    public synchronized void clear() {
        feeds.clear();
    }

    private static int count(Map<UUID, Integer> source, UUID cameraId) {
        int count = source.getOrDefault(cameraId, 0);
        if (count < 0) {
            throw new IllegalArgumentException("Demand counts cannot be negative");
        }
        return count;
    }

    private FeedState requireDemanded(UUID cameraId) {
        FeedState feed = feeds.get(Objects.requireNonNull(cameraId, "cameraId"));
        if (feed == null || (feed.webViewers == 0 && feed.screens == 0)) {
            throw new IllegalStateException("Camera has no demand: " + cameraId);
        }
        return feed;
    }

    private static void moveToUnavailable(FeedState feed) {
        if (feed.lifecycle == FeedLifecycleState.INACTIVE
                || feed.lifecycle == FeedLifecycleState.RETAINING) {
            transition(feed, FeedLifecycleState.ACTIVATING);
        }
        transition(feed, FeedLifecycleState.UNAVAILABLE);
    }

    private static void transition(FeedState feed, FeedLifecycleState next) {
        if (!feed.lifecycle.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid feed transition: "
                    + feed.lifecycle + " -> " + next);
        }
        feed.lifecycle = next;
    }

    private static Demand snapshot(UUID cameraId, FeedState feed) {
        return new Demand(cameraId, feed.webViewers, feed.screens, feed.lifecycle);
    }
}
