package com.matissjurevics.icyou.render.webrtc;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;

/** Bounded non-trickle SDP signaling between the web viewer and render agent. */
public final class WebRtcSignalingProtocol {

    public static final int VERSION = 1;
    public static final int MAX_SDP_CHARS = 64 * 1024;

    public record Offer(UUID peerId, UUID jobId, long jobRevision, UUID cameraId,
                        String sdp) {
        public Offer {
            requireIds(peerId, jobId, cameraId);
            requireRevision(jobRevision);
            sdp = requireSdp(sdp, "offer");
        }
    }

    public record Answer(UUID peerId, UUID jobId, long jobRevision, String sdp) {
        public Answer {
            Objects.requireNonNull(peerId, "peerId");
            Objects.requireNonNull(jobId, "jobId");
            requireRevision(jobRevision);
            sdp = requireSdp(sdp, "answer");
        }
    }

    public record Close(UUID peerId, UUID jobId, long jobRevision) {
        public Close {
            Objects.requireNonNull(peerId, "peerId");
            Objects.requireNonNull(jobId, "jobId");
            requireRevision(jobRevision);
        }
    }

    private WebRtcSignalingProtocol() {
    }

    static void writeOffer(Offer offer, RegistryByteBuf buffer) {
        writeHeader(offer.peerId(), offer.jobId(), offer.jobRevision(), buffer);
        buffer.writeUuid(offer.cameraId());
        buffer.writeString(offer.sdp(), MAX_SDP_CHARS);
    }

    static Offer readOffer(RegistryByteBuf buffer) {
        Header header = readHeader(buffer);
        return new Offer(header.peerId(), header.jobId(), header.revision(),
                buffer.readUuid(), buffer.readString(MAX_SDP_CHARS));
    }

    static void writeAnswer(Answer answer, RegistryByteBuf buffer) {
        writeHeader(answer.peerId(), answer.jobId(), answer.jobRevision(), buffer);
        buffer.writeString(answer.sdp(), MAX_SDP_CHARS);
    }

    static Answer readAnswer(RegistryByteBuf buffer) {
        Header header = readHeader(buffer);
        return new Answer(header.peerId(), header.jobId(), header.revision(),
                buffer.readString(MAX_SDP_CHARS));
    }

    static void writeClose(Close close, RegistryByteBuf buffer) {
        writeHeader(close.peerId(), close.jobId(), close.jobRevision(), buffer);
    }

    static Close readClose(RegistryByteBuf buffer) {
        Header header = readHeader(buffer);
        return new Close(header.peerId(), header.jobId(), header.revision());
    }

    private static void writeHeader(UUID peerId, UUID jobId, long revision,
                                    RegistryByteBuf buffer) {
        buffer.writeVarInt(VERSION);
        buffer.writeUuid(peerId);
        buffer.writeUuid(jobId);
        buffer.writeVarLong(revision);
    }

    private static Header readHeader(RegistryByteBuf buffer) {
        int version = buffer.readVarInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported WebRTC signaling version: "
                    + version);
        }
        return new Header(buffer.readUuid(), buffer.readUuid(), buffer.readVarLong());
    }

    private static String requireSdp(String value, String label) {
        String sdp = Objects.requireNonNull(value, label + "Sdp");
        if (sdp.isBlank() || sdp.length() > MAX_SDP_CHARS || sdp.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid WebRTC " + label + " SDP");
        }
        return sdp;
    }

    private static void requireIds(UUID peerId, UUID jobId, UUID cameraId) {
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(cameraId, "cameraId");
    }

    private static void requireRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("Invalid WebRTC job revision");
        }
    }

    private record Header(UUID peerId, UUID jobId, long revision) {
    }
}
