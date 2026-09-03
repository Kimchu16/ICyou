package com.matissjurevics.icyou.render.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.network.RegistryByteBuf;

/** Versioned control messages shared by the logical server and render agents. */
public final class RenderProtocol {

    public static final int VERSION = CameraOverhaulContracts.RENDER_PROTOCOL_VERSION;
    public static final int PROOF_BYTES = 32;
    public static final int MAX_STATUS_DETAIL = 160;

    private static final int CLIENT_HELLO = 1;
    private static final int CLIENT_AUTH_PROOF = 2;
    private static final int CLIENT_JOB_STATUS = 3;
    private static final int SERVER_AUTH_CHALLENGE = 1;
    private static final int SERVER_AUTH_RESULT = 2;
    private static final int SERVER_JOB_ASSIGNMENT = 3;
    private static final int SERVER_JOB_CANCEL = 4;

    public sealed interface ClientMessage permits AgentHello, AuthProof, JobStatus {
    }

    public sealed interface ServerMessage permits AuthChallenge, AuthResult,
            JobAssignment, JobCancel {
    }

    public enum MediaTransport {
        WEBRTC(1),
        MJPEG(2);

        private final int mask;

        MediaTransport(int mask) {
            this.mask = mask;
        }
    }

    public enum AuthOutcome {
        ACCEPTED(0),
        DENIED(1);

        private final int id;

        AuthOutcome(int id) {
            this.id = id;
        }

        private static AuthOutcome fromId(int id) {
            for (AuthOutcome value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown render auth outcome: " + id);
        }
    }

    public enum JobState {
        ACCEPTED(0),
        AVAILABLE(1),
        FAILED(2);

        private final int id;

        JobState(int id) {
            this.id = id;
        }

        private static JobState fromId(int id) {
            for (JobState value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown render job state: " + id);
        }
    }

    public enum CancelReason {
        DEMAND_ENDED(0),
        REASSIGNED(1),
        CAMERA_MOVED(2),
        SERVER_STOPPING(3);

        private final int id;

        CancelReason(int id) {
            this.id = id;
        }

        private static CancelReason fromId(int id) {
            for (CancelReason value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown render cancel reason: " + id);
        }
    }

    public record AgentHello(UUID credentialId, int capacity,
                             Set<MediaTransport> transports) implements ClientMessage {
        public AgentHello {
            Objects.requireNonNull(credentialId, "credentialId");
            transports = Set.copyOf(Objects.requireNonNull(transports, "transports"));
            if (capacity < 1 || capacity > CameraOverhaulContracts.MAX_ACTIVE_CAMERAS) {
                throw new IllegalArgumentException("Invalid render-agent capacity: " + capacity);
            }
            if (transports.isEmpty()) {
                throw new IllegalArgumentException("Render agent must support a media transport");
            }
        }
    }

    public record AuthProof(UUID challengeId, byte[] proof) implements ClientMessage {
        public AuthProof {
            Objects.requireNonNull(challengeId, "challengeId");
            proof = requireProof(proof, "proof");
        }

        @Override
        public byte[] proof() {
            return proof.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AuthProof that
                    && challengeId.equals(that.challengeId)
                    && Arrays.equals(proof, that.proof);
        }

        @Override
        public int hashCode() {
            return 31 * challengeId.hashCode() + Arrays.hashCode(proof);
        }
    }

    public record JobStatus(UUID jobId, long revision, JobState state,
                            String detail) implements ClientMessage {
        public JobStatus {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(state, "state");
            detail = requireDetail(detail);
            requireRevision(revision);
        }
    }

    public record AuthChallenge(UUID challengeId, byte[] nonce) implements ServerMessage {
        public AuthChallenge {
            Objects.requireNonNull(challengeId, "challengeId");
            nonce = requireProof(nonce, "nonce");
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AuthChallenge that
                    && challengeId.equals(that.challengeId)
                    && Arrays.equals(nonce, that.nonce);
        }

        @Override
        public int hashCode() {
            return 31 * challengeId.hashCode() + Arrays.hashCode(nonce);
        }
    }

    public record AuthResult(UUID challengeId, AuthOutcome outcome,
                             Optional<UUID> sessionId) implements ServerMessage {
        public AuthResult {
            Objects.requireNonNull(challengeId, "challengeId");
            Objects.requireNonNull(outcome, "outcome");
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            if ((outcome == AuthOutcome.ACCEPTED) != sessionId.isPresent()) {
                throw new IllegalArgumentException(
                        "Accepted auth requires a session; rejected auth must not include one");
            }
        }
    }

    public record JobAssignment(UUID jobId, long revision, CameraRef camera,
                                int width, int height, int framesPerSecond)
            implements ServerMessage {
        public JobAssignment {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(camera, "camera");
            requireRevision(revision);
            if (width != CameraOverhaulContracts.VIDEO_WIDTH
                    || height != CameraOverhaulContracts.VIDEO_HEIGHT
                    || framesPerSecond != CameraOverhaulContracts.VIDEO_FPS) {
                throw new IllegalArgumentException("Unsupported render job video contract");
            }
        }
    }

    public record JobCancel(UUID jobId, long revision, CancelReason reason)
            implements ServerMessage {
        public JobCancel {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(reason, "reason");
            requireRevision(revision);
        }
    }

    private RenderProtocol() {
    }

    public static void writeClient(ClientMessage message, RegistryByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        writeHeader(buffer);
        switch (message) {
            case AgentHello hello -> {
                buffer.writeByte(CLIENT_HELLO);
                buffer.writeUuid(hello.credentialId());
                buffer.writeVarInt(hello.capacity());
                buffer.writeByte(transportMask(hello.transports()));
            }
            case AuthProof proof -> {
                buffer.writeByte(CLIENT_AUTH_PROOF);
                buffer.writeUuid(proof.challengeId());
                buffer.writeByteArray(proof.proof());
            }
            case JobStatus status -> {
                buffer.writeByte(CLIENT_JOB_STATUS);
                buffer.writeUuid(status.jobId());
                buffer.writeVarLong(status.revision());
                buffer.writeByte(status.state().id);
                buffer.writeString(status.detail(), MAX_STATUS_DETAIL);
            }
        }
    }

    public static ClientMessage readClient(RegistryByteBuf buffer) {
        readHeader(buffer);
        return switch (buffer.readUnsignedByte()) {
            case CLIENT_HELLO -> new AgentHello(buffer.readUuid(), buffer.readVarInt(),
                    transports(buffer.readUnsignedByte()));
            case CLIENT_AUTH_PROOF -> new AuthProof(buffer.readUuid(),
                    buffer.readByteArray(PROOF_BYTES));
            case CLIENT_JOB_STATUS -> new JobStatus(buffer.readUuid(), buffer.readVarLong(),
                    JobState.fromId(buffer.readUnsignedByte()),
                    buffer.readString(MAX_STATUS_DETAIL));
            default -> throw new IllegalArgumentException("Unknown render client message");
        };
    }

    public static void writeServer(ServerMessage message, RegistryByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        writeHeader(buffer);
        switch (message) {
            case AuthChallenge challenge -> {
                buffer.writeByte(SERVER_AUTH_CHALLENGE);
                buffer.writeUuid(challenge.challengeId());
                buffer.writeByteArray(challenge.nonce());
            }
            case AuthResult result -> {
                buffer.writeByte(SERVER_AUTH_RESULT);
                buffer.writeUuid(result.challengeId());
                buffer.writeByte(result.outcome().id);
                buffer.writeBoolean(result.sessionId().isPresent());
                result.sessionId().ifPresent(buffer::writeUuid);
            }
            case JobAssignment job -> {
                buffer.writeByte(SERVER_JOB_ASSIGNMENT);
                buffer.writeUuid(job.jobId());
                buffer.writeVarLong(job.revision());
                CameraRef.PACKET_CODEC.encode(buffer, job.camera());
                buffer.writeVarInt(job.width());
                buffer.writeVarInt(job.height());
                buffer.writeVarInt(job.framesPerSecond());
            }
            case JobCancel cancel -> {
                buffer.writeByte(SERVER_JOB_CANCEL);
                buffer.writeUuid(cancel.jobId());
                buffer.writeVarLong(cancel.revision());
                buffer.writeByte(cancel.reason().id);
            }
        }
    }

    public static ServerMessage readServer(RegistryByteBuf buffer) {
        readHeader(buffer);
        return switch (buffer.readUnsignedByte()) {
            case SERVER_AUTH_CHALLENGE -> new AuthChallenge(buffer.readUuid(),
                    buffer.readByteArray(PROOF_BYTES));
            case SERVER_AUTH_RESULT -> readAuthResult(buffer);
            case SERVER_JOB_ASSIGNMENT -> new JobAssignment(buffer.readUuid(),
                    buffer.readVarLong(), CameraRef.PACKET_CODEC.decode(buffer),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
            case SERVER_JOB_CANCEL -> new JobCancel(buffer.readUuid(), buffer.readVarLong(),
                    CancelReason.fromId(buffer.readUnsignedByte()));
            default -> throw new IllegalArgumentException("Unknown render server message");
        };
    }

    private static AuthResult readAuthResult(RegistryByteBuf buffer) {
        UUID challengeId = buffer.readUuid();
        AuthOutcome outcome = AuthOutcome.fromId(buffer.readUnsignedByte());
        Optional<UUID> sessionId = buffer.readBoolean()
                ? Optional.of(buffer.readUuid()) : Optional.empty();
        return new AuthResult(challengeId, outcome, sessionId);
    }

    private static void writeHeader(RegistryByteBuf buffer) {
        buffer.writeVarInt(VERSION);
    }

    private static void readHeader(RegistryByteBuf buffer) {
        int version = buffer.readVarInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported render protocol version: " + version);
        }
    }

    private static int transportMask(Set<MediaTransport> transports) {
        return transports.stream().mapToInt(transport -> transport.mask).reduce(0, (a, b) -> a | b);
    }

    private static Set<MediaTransport> transports(int mask) {
        int knownMask = MediaTransport.WEBRTC.mask | MediaTransport.MJPEG.mask;
        if (mask == 0 || (mask & ~knownMask) != 0) {
            throw new IllegalArgumentException("Invalid render media transport mask: " + mask);
        }
        java.util.EnumSet<MediaTransport> result = java.util.EnumSet.noneOf(MediaTransport.class);
        for (MediaTransport transport : MediaTransport.values()) {
            if ((mask & transport.mask) != 0) {
                result.add(transport);
            }
        }
        return Set.copyOf(result);
    }

    private static byte[] requireProof(byte[] value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length != PROOF_BYTES) {
            throw new IllegalArgumentException(label + " must contain " + PROOF_BYTES + " bytes");
        }
        return value.clone();
    }

    private static String requireDetail(String detail) {
        Objects.requireNonNull(detail, "detail");
        if (detail.length() > MAX_STATUS_DETAIL
                || detail.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("Invalid render job status detail");
        }
        return detail;
    }

    private static void requireRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("Render job revision cannot be negative");
        }
    }
}
