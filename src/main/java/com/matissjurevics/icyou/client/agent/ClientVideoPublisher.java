package com.matissjurevics.icyou.client.agent;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import com.matissjurevics.icyou.render.video.VideoFrameProtocol.Frame;

import net.minecraft.client.MinecraftClient;

/** Encodes one latest frame at a time and publishes no queued stale frames. */
final class ClientVideoPublisher implements AutoCloseable {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final Function<RemoteVideoFrame, byte[]> encoder;
    private final Consumer<Frame> sink;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("icyou-jpeg-", 0).factory());
    private final AtomicBoolean encoding = new AtomicBoolean();
    private final Map<UUID, Long> submitted = new LinkedHashMap<>();
    private final Map<UUID, Integer> failures = new LinkedHashMap<>();
    private final Set<UUID> announced = new LinkedHashSet<>();
    private volatile boolean closed;

    ClientVideoPublisher(Function<RemoteVideoFrame, byte[]> encoder,
                         Consumer<Frame> sink) {
        this.encoder = java.util.Objects.requireNonNull(encoder, "encoder");
        this.sink = java.util.Objects.requireNonNull(sink, "sink");
    }

    void tick(MinecraftClient client) {
        Set<UUID> activeJobs = ClientRenderAgentLifecycle.agent().activeJobs().keySet();
        submitted.keySet().retainAll(activeJobs);
        failures.keySet().retainAll(activeJobs);
        announced.retainAll(activeJobs);
        if (closed || !encoding.compareAndSet(false, true)) {
            return;
        }
        RemoteVideoFrame selected = RemoteFrameStore.frames().values().stream()
                .filter(frame -> activeJobs.contains(frame.jobId()))
                .filter(frame -> submitted.getOrDefault(frame.jobId(), -1L)
                        < frame.sequence())
                .min(Comparator.comparingLong(RemoteVideoFrame::capturedAtMillis)
                        .thenComparing(frame -> frame.jobId().toString()))
                .orElse(null);
        if (selected == null) {
            encoding.set(false);
            return;
        }
        submitted.put(selected.jobId(), selected.sequence());
        worker.execute(() -> encode(client, selected));
    }

    private void encode(MinecraftClient client, RemoteVideoFrame source) {
        byte[] jpeg = null;
        Throwable failure = null;
        try {
            jpeg = encoder.apply(source);
        } catch (Throwable error) {
            failure = error;
        }
        byte[] completedJpeg = jpeg;
        Throwable completedFailure = failure;
        client.execute(() -> complete(source, completedJpeg, completedFailure));
    }

    private void complete(RemoteVideoFrame source, byte[] jpeg, Throwable failure) {
        encoding.set(false);
        if (closed) {
            return;
        }
        var job = ClientRenderAgentLifecycle.agent().activeJobs().get(source.jobId());
        RemoteVideoFrame latest = RemoteFrameStore.get(source.jobId());
        if (job == null || job.revision() != source.jobRevision() || latest == null) {
            return;
        }
        Throwable deliveryFailure = failure;
        if (deliveryFailure == null) {
            try {
                sink.accept(new Frame(source.jobId(), source.jobRevision(), source.cameraId(),
                        source.sequence(), source.capturedAtMillis(), jpeg));
            } catch (Throwable error) {
                deliveryFailure = error;
            }
        }
        if (deliveryFailure != null) {
            submitted.remove(source.jobId());
            int count = failures.merge(source.jobId(), 1, Integer::sum);
            if (count >= MAX_CONSECUTIVE_FAILURES) {
                ClientRenderAgentLifecycle.agent().markFailed(source.jobId(),
                        "JPEG video delivery failed");
            }
            return;
        }
        failures.remove(source.jobId());
        if (announced.add(source.jobId())) {
            ClientRenderAgentLifecycle.agent().markAvailable(source.jobId());
        }
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
        submitted.clear();
        failures.clear();
        announced.clear();
    }
}
