package com.matissjurevics.icyou.client.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.matissjurevics.icyou.client.agent.SceneSnapshotAssembler.CompleteSnapshot;

/** Owns exact-snapshot resources and closes them on replacement or removal. */
final class RemoteSceneWorldRegistry<T extends AutoCloseable> {

    private record Entry<T>(long revision, long sequence, T resource) {
    }

    private final Map<UUID, Entry<T>> entries = new LinkedHashMap<>();

    T install(CompleteSnapshot snapshot, Function<CompleteSnapshot, T> factory) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(factory, "factory");
        UUID jobId = snapshot.begin().jobId();
        Entry<T> current = entries.get(jobId);
        if (current != null && current.revision == snapshot.begin().jobRevision()
                && current.sequence == snapshot.begin().sequence()) {
            return current.resource;
        }
        remove(jobId);
        T resource = Objects.requireNonNull(factory.apply(snapshot), "resource");
        entries.put(jobId, new Entry<>(snapshot.begin().jobRevision(),
                snapshot.begin().sequence(), resource));
        return resource;
    }

    T get(UUID jobId) {
        Entry<T> entry = entries.get(Objects.requireNonNull(jobId, "jobId"));
        return entry == null ? null : entry.resource;
    }

    Map<UUID, T> resources() {
        Map<UUID, T> result = new LinkedHashMap<>();
        entries.forEach((jobId, entry) -> result.put(jobId, entry.resource));
        return Map.copyOf(result);
    }

    void retain(Set<UUID> jobIds) {
        Objects.requireNonNull(jobIds, "jobIds");
        entries.keySet().stream().filter(jobId -> !jobIds.contains(jobId)).toList()
                .forEach(this::remove);
    }

    void remove(UUID jobId) {
        Entry<T> entry = entries.remove(Objects.requireNonNull(jobId, "jobId"));
        if (entry != null) {
            close(entry.resource);
        }
    }

    void clear() {
        entries.values().stream().map(Entry::resource).toList().forEach(this::close);
        entries.clear();
    }

    private void close(T resource) {
        try {
            resource.close();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to close remote scene resource", error);
        }
    }
}
