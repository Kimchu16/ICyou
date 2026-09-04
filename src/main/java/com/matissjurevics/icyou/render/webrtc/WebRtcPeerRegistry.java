package com.matissjurevics.icyou.render.webrtc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded transient browser-to-agent signaling state. */
public final class WebRtcPeerRegistry {

    public static final int MAX_PEERS = 16;
    public static final int MAX_PEERS_PER_AGENT = 16;
    public static final Duration PEER_TIMEOUT = Duration.ofSeconds(30);

    public record Binding(UUID peerId, UUID viewerSessionId, UUID cameraId,
                          UUID jobId, long jobRevision, UUID agentId,
                          UUID agentSessionId, Instant lastSeen) {
        public Binding {
            Objects.requireNonNull(peerId, "peerId");
            Objects.requireNonNull(viewerSessionId, "viewerSessionId");
            Objects.requireNonNull(cameraId, "cameraId");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(agentSessionId, "agentSessionId");
            Objects.requireNonNull(lastSeen, "lastSeen");
            if (jobRevision < 0) throw new IllegalArgumentException("Invalid job revision");
        }

        Binding seen(Instant now) {
            return new Binding(peerId, viewerSessionId, cameraId, jobId, jobRevision,
                    agentId, agentSessionId, now);
        }
    }

    public record Opened(Binding binding, String offerSdp) {
    }

    public record Poll(Binding binding, Optional<String> answerSdp) {
    }

    private static final class Peer {
        private Binding binding;
        private final String offer;
        private String answer;

        private Peer(Binding binding, String offer) {
            this.binding = binding;
            this.offer = offer;
        }
    }

    private final Map<UUID, Peer> peers = new LinkedHashMap<>();
    private final int maxPeers;

    public WebRtcPeerRegistry() {
        this(MAX_PEERS);
    }

    public WebRtcPeerRegistry(int maxPeers) {
        if (maxPeers < 1) throw new IllegalArgumentException("Peer limit must be positive");
        this.maxPeers = maxPeers;
    }

    public synchronized Optional<Opened> open(UUID viewerSessionId, UUID cameraId,
            UUID jobId, long revision, UUID agentId, UUID agentSessionId,
            String offerSdp, Instant now) {
        expire(now);
        long agentPeers = peers.values().stream()
                .filter(peer -> peer.binding.agentId().equals(agentId)).count();
        if (peers.size() >= maxPeers || agentPeers >= MAX_PEERS_PER_AGENT) {
            return Optional.empty();
        }
        UUID peerId = UUID.randomUUID();
        Binding binding = new Binding(peerId, viewerSessionId, cameraId, jobId,
                revision, agentId, agentSessionId, now);
        String offer = requireSdp(offerSdp);
        peers.put(peerId, new Peer(binding, offer));
        return Optional.of(new Opened(binding, offer));
    }

    public synchronized boolean answer(UUID peerId, UUID jobId, long revision,
                                       UUID agentId, UUID agentSessionId,
                                       String answerSdp) {
        Peer peer = peers.get(peerId);
        if (peer == null || !peer.binding.jobId().equals(jobId)
                || peer.binding.jobRevision() != revision
                || !peer.binding.agentId().equals(agentId)
                || !peer.binding.agentSessionId().equals(agentSessionId)
                || peer.answer != null) return false;
        peer.answer = requireSdp(answerSdp);
        return true;
    }

    public synchronized Optional<Poll> poll(UUID peerId, UUID viewerSessionId,
                                            UUID cameraId, Instant now) {
        expire(now);
        Peer peer = peers.get(peerId);
        if (peer == null || !peer.binding.viewerSessionId().equals(viewerSessionId)
                || !peer.binding.cameraId().equals(cameraId)) return Optional.empty();
        peer.binding = peer.binding.seen(now);
        return Optional.of(new Poll(peer.binding, Optional.ofNullable(peer.answer)));
    }

    public synchronized Optional<Binding> close(UUID peerId, UUID viewerSessionId,
                                                UUID cameraId) {
        Peer peer = peers.get(peerId);
        if (peer == null || !peer.binding.viewerSessionId().equals(viewerSessionId)
                || !peer.binding.cameraId().equals(cameraId)) return Optional.empty();
        peers.remove(peerId);
        return Optional.of(peer.binding);
    }

    public synchronized List<Binding> expire(Instant now) {
        Instant cutoff = Objects.requireNonNull(now, "now").minus(PEER_TIMEOUT);
        List<Binding> expired = peers.values().stream()
                .map(peer -> peer.binding)
                .filter(binding -> !binding.lastSeen().isAfter(cutoff)).toList();
        expired.forEach(binding -> peers.remove(binding.peerId()));
        return expired;
    }

    public synchronized List<Binding> removeInvalid(
            java.util.function.Predicate<Binding> valid) {
        List<Binding> removed = new ArrayList<>();
        peers.values().removeIf(peer -> {
            boolean invalid = !valid.test(peer.binding);
            if (invalid) removed.add(peer.binding);
            return invalid;
        });
        return List.copyOf(removed);
    }

    public synchronized List<Binding> clear() {
        List<Binding> result = peers.values().stream().map(peer -> peer.binding).toList();
        peers.clear();
        return result;
    }

    public synchronized int size() { return peers.size(); }

    private static String requireSdp(String sdp) {
        String value = Objects.requireNonNull(sdp, "sdp");
        if (value.isBlank() || value.length() > WebRtcSignalingProtocol.MAX_SDP_CHARS
                || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid SDP");
        return value;
    }
}
