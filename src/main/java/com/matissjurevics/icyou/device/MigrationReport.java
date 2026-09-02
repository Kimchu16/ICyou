package com.matissjurevics.icyou.device;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Human-readable evidence and ambiguity log for the one-time 0.2.0 migration. */
final class MigrationReport {

    private final Instant startedAt;
    private final List<String> details = new ArrayList<>();
    private int terminals;
    private int cameras;
    private int screens;
    private int ambiguities;

    MigrationReport(Instant startedAt) {
        this.startedAt = startedAt;
    }

    void migratedTerminal() {
        terminals++;
    }

    void migratedCamera() {
        cameras++;
    }

    void migratedScreen() {
        screens++;
    }

    void note(String detail) {
        details.add("INFO: " + detail);
    }

    void ambiguity(String detail) {
        ambiguities++;
        details.add("AMBIGUITY: " + detail);
    }

    int ambiguityCount() {
        return ambiguities;
    }

    String render() {
        StringBuilder text = new StringBuilder();
        text.append("ICyou 0.2.0 -> 0.3.0 device migration\n")
                .append("Started: ").append(startedAt).append('\n')
                .append("Terminals migrated: ").append(terminals).append('\n')
                .append("Cameras migrated: ").append(cameras).append('\n')
                .append("Screens migrated: ").append(screens).append('\n')
                .append("Ambiguities: ").append(ambiguities).append("\n\n");
        if (details.isEmpty()) {
            text.append("No ambiguities were found.\n");
        } else {
            details.forEach(line -> text.append(line).append('\n'));
        }
        return text.toString();
    }
}
