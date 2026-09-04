package com.matissjurevics.icyou.client.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.render.webrtc.WebRtcSignalingProtocol.Answer;
import com.matissjurevics.icyou.render.webrtc.WebRtcSignalingProtocol.Close;
import com.matissjurevics.icyou.render.webrtc.WebRtcSignalingProtocol.Offer;

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceGatheringState;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import dev.onvoid.webrtc.media.audio.AudioTrack;
import dev.onvoid.webrtc.media.audio.CustomAudioSource;
import dev.onvoid.webrtc.media.SyncClock;
import dev.onvoid.webrtc.media.video.CustomVideoSource;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;

/** Owns bounded native WebRTC peers and shared per-job custom media tracks. */
final class WebRtcAgent implements AutoCloseable {

    private static final int MAX_PEERS = 16;
    private final PeerConnectionFactory factory = new PeerConnectionFactory();
    private final Consumer<Answer> answers;
    private final Map<UUID, Peer> peers = new java.util.LinkedHashMap<>();
    private final Map<UUID, JobMedia> media = new ConcurrentHashMap<>();
    private final ScheduledExecutorService audio = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("icyou-webrtc-audio").factory());
    private final ThreadPoolExecutor decoder = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(512),
            Thread.ofVirtual().name("icyou-sound-decode-", 0).factory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private boolean closed;

    WebRtcAgent(Consumer<Answer> answers) {
        this.answers = answers;
        audio.scheduleAtFixedRate(this::pushAudio, 0, 10, TimeUnit.MILLISECONDS);
    }

    void offer(MinecraftClient client, Offer offer) {
        var job = ClientRenderAgentLifecycle.agent().activeJobs().get(offer.jobId());
        if (closed || peers.size() >= MAX_PEERS || peers.containsKey(offer.peerId())
                || job == null || job.revision() != offer.jobRevision()
                || !job.camera().deviceId().equals(offer.cameraId())) return;
        JobMedia jobMedia = null;
        try {
            jobMedia = media.computeIfAbsent(offer.jobId(), ignored ->
                    new JobMedia(offer.jobId()));
            Peer peer = new Peer(client, offer, jobMedia);
            peers.put(offer.peerId(), peer);
            jobMedia.peerCount++;
            peer.start();
        } catch (LinkageError | RuntimeException error) {
            if (peers.containsKey(offer.peerId())) {
                remove(offer.peerId());
            } else if (jobMedia != null && jobMedia.peerCount == 0
                    && media.remove(jobMedia.jobId, jobMedia)) {
                jobMedia.close();
            }
        }
    }

    void close(Close close) {
        Peer peer = peers.get(close.peerId());
        if (peer != null && peer.offer.jobId().equals(close.jobId())
                && peer.offer.jobRevision() == close.jobRevision()) remove(close.peerId());
    }

    void tick() {
        if (closed) return;
        var active = ClientRenderAgentLifecycle.agent().activeJobs();
        new ArrayList<>(peers.values()).stream()
                .filter(peer -> {
                    var job = active.get(peer.offer.jobId());
                    return job == null || job.revision() != peer.offer.jobRevision();
                }).map(peer -> peer.offer.peerId()).toList().forEach(this::remove);
        for (JobMedia jobMedia : media.values()) {
            enqueueAudio(jobMedia);
            RemoteVideoFrame frame = RemoteFrameStore.get(jobMedia.jobId);
            if (frame == null || frame.sequence() <= jobMedia.lastVideoSequence) continue;
            try {
                VideoFrame converted = I420FrameConverter.convert(frame,
                        CameraOverhaulContracts.VIDEO_WIDTH,
                        CameraOverhaulContracts.VIDEO_HEIGHT,
                        jobMedia.clock.getTimestampUs() * 1_000L);
                try {
                    jobMedia.videoSource.pushFrame(converted);
                    jobMedia.lastVideoSequence = frame.sequence();
                    if (!jobMedia.available) {
                        jobMedia.available = true;
                        ClientRenderAgentLifecycle.agent().markAvailable(jobMedia.jobId);
                    }
                } finally {
                    converted.release();
                }
            } catch (RuntimeException error) {
                peers.values().stream().filter(peer -> peer.offer.jobId().equals(jobMedia.jobId))
                        .map(peer -> peer.offer.peerId()).toList().forEach(this::remove);
            }
        }
    }

    private void pushAudio() {
        if (closed) return;
        for (JobMedia jobMedia : List.copyOf(media.values())) {
            try {
                jobMedia.pushAudio();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void enqueueAudio(JobMedia jobMedia) {
        var job = ClientRenderAgentLifecycle.agent().activeJobs().get(jobMedia.jobId);
        var scene = ClientRemoteSceneLifecycle.worlds().get(jobMedia.jobId);
        if (job == null || scene == null) return;
        Direction facing = scene.world().getBlockState(job.camera().position())
                .getOrEmpty(CameraBlock.FACING).orElse(Direction.NORTH);
        double rightX = -facing.getOffsetZ();
        double rightZ = facing.getOffsetX();
        if (facing.getAxis().isVertical()) { rightX = 1; rightZ = 0; }
        double x = job.camera().position().getX() + 0.5;
        double y = job.camera().position().getY() + 0.5;
        double z = job.camera().position().getZ() + 0.5;
        for (var event : ClientAudioSceneLifecycle.store().drain(jobMedia.jobId)) {
            MinecraftSoundDecoder.Resolved resolved = MinecraftSoundDecoder.resolve(
                    MinecraftClient.getInstance(), event);
            if (resolved == null) continue;
            double finalRightX = rightX;
            double finalRightZ = rightZ;
            decoder.execute(() -> {
                try {
                    var decoded = MinecraftSoundDecoder.decode(
                            MinecraftClient.getInstance(), resolved);
                    jobMedia.mixer.add(event, decoded, x, y, z,
                            finalRightX, finalRightZ);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void remove(UUID peerId) {
        Peer peer = peers.remove(peerId);
        if (peer == null) return;
        peer.connection.close();
        if (--peer.jobMedia.peerCount == 0) {
            media.remove(peer.jobMedia.jobId);
            peer.jobMedia.close();
        }
    }

    @Override
    public void close() {
        closed = true;
        audio.shutdownNow();
        decoder.shutdownNow();
        List.copyOf(peers.keySet()).forEach(this::remove);
        media.values().forEach(JobMedia::close);
        media.clear();
        factory.dispose();
    }

    private final class JobMedia implements AutoCloseable {
        private final UUID jobId;
        private final SyncClock clock = new SyncClock();
        private final CustomVideoSource videoSource = new CustomVideoSource(clock);
        private final CustomAudioSource audioSource = new CustomAudioSource(clock);
        private final VideoTrack videoTrack;
        private final AudioTrack audioTrack;
        private final RemoteAudioMixer mixer = new RemoteAudioMixer();
        private int peerCount;
        private long lastVideoSequence = -1;
        private boolean available;
        private boolean mediaClosed;

        private JobMedia(UUID jobId) {
            this.jobId = jobId;
            videoTrack = factory.createVideoTrack("video-" + jobId, videoSource);
            audioTrack = factory.createAudioTrack("audio-" + jobId, audioSource);
        }

        private synchronized void pushAudio() {
            if (!mediaClosed) {
                audioSource.pushAudio(mixer.mix10ms(), 16,
                        RemoteAudioMixer.SAMPLE_RATE, RemoteAudioMixer.CHANNELS,
                        RemoteAudioMixer.FRAMES_10_MS);
            }
        }

        @Override public synchronized void close() {
            if (mediaClosed) return;
            mediaClosed = true;
            audioTrack.dispose();
            videoTrack.dispose();
            audioSource.dispose();
            videoSource.dispose();
            clock.dispose();
            mixer.clear();
        }
    }

    private final class Peer {
        private final MinecraftClient client;
        private final Offer offer;
        private final JobMedia jobMedia;
        private final RTCPeerConnection connection;
        private final AtomicBoolean answerSent = new AtomicBoolean();

        private Peer(MinecraftClient client, Offer offer, JobMedia jobMedia) {
            this.client = client;
            this.offer = offer;
            this.jobMedia = jobMedia;
            RTCIceServer ice = new RTCIceServer();
            ice.urls.add("stun:stun.l.google.com:19302");
            RTCConfiguration configuration = new RTCConfiguration();
            configuration.iceServers.add(ice);
            connection = factory.createPeerConnection(configuration, new Observer());
            if (connection == null) {
                throw new IllegalStateException("Could not create WebRTC peer");
            }
            try {
                connection.addTrack(jobMedia.audioTrack, List.of("camera-" + offer.jobId()));
                connection.addTrack(jobMedia.videoTrack, List.of("camera-" + offer.jobId()));
            } catch (LinkageError | RuntimeException error) {
                connection.close();
                throw error;
            }
        }

        private void start() {
            connection.setRemoteDescription(new RTCSessionDescription(
                    RTCSdpType.OFFER, offer.sdp()), new SetSessionDescriptionObserver() {
                @Override public void onSuccess() {
                    connection.createAnswer(new RTCAnswerOptions(),
                            new CreateSessionDescriptionObserver() {
                        @Override public void onSuccess(RTCSessionDescription answer) {
                            connection.setLocalDescription(answer,
                                    new SetSessionDescriptionObserver() {
                                @Override public void onSuccess() { emitAnswerIfComplete(); }
                                @Override public void onFailure(String error) { fail(); }
                            });
                        }
                        @Override public void onFailure(String error) { fail(); }
                    });
                }
                @Override public void onFailure(String error) { fail(); }
            });
        }

        private void emitAnswerIfComplete() {
            if (connection.getIceGatheringState() != RTCIceGatheringState.COMPLETE
                    || !answerSent.compareAndSet(false, true)) return;
            RTCSessionDescription description = connection.getLocalDescription();
            if (description == null) { fail(); return; }
            client.execute(() -> answers.accept(new Answer(offer.peerId(), offer.jobId(),
                    offer.jobRevision(), description.sdp)));
        }

        private void fail() { client.execute(() -> remove(offer.peerId())); }

        private final class Observer implements PeerConnectionObserver {
            @Override public void onIceCandidate(RTCIceCandidate candidate) {
                // Non-trickle signaling waits for the complete SDP.
            }
            @Override public void onIceGatheringChange(RTCIceGatheringState state) {
                if (state == RTCIceGatheringState.COMPLETE) emitAnswerIfComplete();
            }
            @Override public void onConnectionChange(RTCPeerConnectionState state) {
                if (state == RTCPeerConnectionState.FAILED
                        || state == RTCPeerConnectionState.CLOSED) fail();
            }
        }
    }
}
