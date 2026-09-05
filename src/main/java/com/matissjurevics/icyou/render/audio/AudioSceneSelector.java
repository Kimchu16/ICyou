package com.matissjurevics.icyou.render.audio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Selects bounded camera-audible events while excluding render-agent sources. */
final class AudioSceneSelector {

    record Selection(List<AudioSceneProtocol.Event> events, boolean truncated) {
        Selection {
            events = List.copyOf(events);
        }
    }

    private AudioSceneSelector() {
    }

    static Selection select(AudioSceneJournal.Capture capture,
                            double cameraX, double cameraY, double cameraZ,
                            Predicate<UUID> excludedSource) {
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(excludedSource, "excludedSource");
        List<AudioSceneProtocol.Event> candidates = capture.events().stream()
                .filter(sound -> sound.sourceEntityId() == null
                        || !excludedSource.test(sound.sourceEntityId()))
                .map(AudioSceneJournal.Captured::event)
                .filter(sound -> AudioCapturePolicy.audible(
                        cameraX, cameraY, cameraZ, sound))
                .toList();
        return new Selection(candidates.stream()
                .limit(AudioSceneProtocol.MAX_EVENTS_PER_BATCH).toList(),
                capture.overflowed()
                        || candidates.size() > AudioSceneProtocol.MAX_EVENTS_PER_BATCH);
    }
}
