package com.matissjurevics.icyou.render.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AgentHello;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthChallenge;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthOutcome;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthProof;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthResult;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobCancel;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobState;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobStatus;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class RenderProtocolTest {

    private static final CameraRef CAMERA = new CameraRef(uuid(1), World.OVERWORLD,
            new BlockPos(10, 64, 20));

    @Test
    void allClientMessagesRoundTrip() {
        AgentHello hello = new AgentHello(uuid(2), 2,
                Set.of(MediaTransport.WEBRTC, MediaTransport.MJPEG));
        AuthProof proof = new AuthProof(uuid(3), bytes(7));
        JobStatus status = new JobStatus(uuid(4), 5, JobState.AVAILABLE, "ready");

        assertEquals(hello, clientRoundTrip(hello));
        assertEquals(proof, clientRoundTrip(proof));
        assertEquals(status, clientRoundTrip(status));
    }

    @Test
    void allServerMessagesRoundTrip() {
        AuthChallenge challenge = new AuthChallenge(uuid(5), bytes(9));
        AuthResult accepted = new AuthResult(uuid(5), AuthOutcome.ACCEPTED,
                Optional.of(uuid(6)));
        AuthResult denied = new AuthResult(uuid(7), AuthOutcome.DENIED, Optional.empty());
        JobAssignment assignment = new JobAssignment(uuid(8), 3, CAMERA,
                CameraOverhaulContracts.VIDEO_WIDTH, CameraOverhaulContracts.VIDEO_HEIGHT,
                CameraOverhaulContracts.VIDEO_FPS);
        JobCancel cancel = new JobCancel(uuid(8), 4, CancelReason.REASSIGNED);

        assertEquals(challenge, serverRoundTrip(challenge));
        assertEquals(accepted, serverRoundTrip(accepted));
        assertEquals(denied, serverRoundTrip(denied));
        assertEquals(assignment, serverRoundTrip(assignment));
        assertEquals(cancel, serverRoundTrip(cancel));
    }

    @Test
    void proofAndNonceAreDefensivelyCopied() {
        byte[] source = bytes(11);
        AuthProof proof = new AuthProof(uuid(9), source);
        AuthChallenge challenge = new AuthChallenge(uuid(10), source);
        source[0] = 99;

        assertEquals(11, proof.proof()[0]);
        assertEquals(11, challenge.nonce()[0]);
        assertNotSame(proof.proof(), proof.proof());
        assertNotSame(challenge.nonce(), challenge.nonce());
        assertArrayEquals(proof.proof(), new AuthProof(uuid(9), bytes(11)).proof());
    }

    @Test
    void rejectsUnknownVersionsAndMessageKinds() {
        assertThrows(IllegalArgumentException.class, () -> decodeClient(buffer -> {
            buffer.writeVarInt(RenderProtocol.VERSION + 1);
            buffer.writeByte(1);
        }));
        assertThrows(IllegalArgumentException.class, () -> decodeClient(buffer -> {
            buffer.writeVarInt(RenderProtocol.VERSION);
            buffer.writeByte(99);
        }));
        assertThrows(IllegalArgumentException.class, () -> decodeClient(buffer -> {
            buffer.writeVarInt(RenderProtocol.VERSION);
            buffer.writeByte(1);
            buffer.writeUuid(uuid(1));
            buffer.writeVarInt(1);
            buffer.writeByte(4);
        }));
    }

    @Test
    void rejectsInvalidCapabilitiesProofsAndJobFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentHello(uuid(1), 0, Set.of(MediaTransport.WEBRTC)));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentHello(uuid(1),
                        CameraOverhaulContracts.MAX_ACTIVE_CAMERAS + 1,
                        Set.of(MediaTransport.WEBRTC)));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentHello(uuid(1), 1, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthProof(uuid(1), new byte[31]));
        assertThrows(IllegalArgumentException.class,
                () -> new JobStatus(uuid(1), -1, JobState.FAILED, "bad"));
        assertThrows(IllegalArgumentException.class,
                () -> new JobStatus(uuid(1), 1, JobState.FAILED, "bad\nstatus"));
        assertThrows(IllegalArgumentException.class,
                () -> new JobStatus(uuid(1), 1, JobState.FAILED,
                        "x".repeat(RenderProtocol.MAX_STATUS_DETAIL + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new JobAssignment(uuid(1), 1, CAMERA, 1, 1, 1));
    }

    @Test
    void authResultCannotLeakSessionOnDenialOrOmitAcceptedSession() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthResult(uuid(1), AuthOutcome.DENIED, Optional.of(uuid(2))));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthResult(uuid(1), AuthOutcome.ACCEPTED, Optional.empty()));
    }

    private static RenderProtocol.ClientMessage clientRoundTrip(
            RenderProtocol.ClientMessage message) {
        return roundTrip(RenderControlC2SPayload.CODEC,
                new RenderControlC2SPayload(message)).message();
    }

    private static RenderProtocol.ServerMessage serverRoundTrip(
            RenderProtocol.ServerMessage message) {
        return roundTrip(RenderControlS2CPayload.CODEC,
                new RenderControlS2CPayload(message)).message();
    }

    private static <T> T roundTrip(PacketCodec<RegistryByteBuf, T> codec, T value) {
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void decodeClient(java.util.function.Consumer<RegistryByteBuf> writer) {
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            writer.accept(buffer);
            RenderProtocol.readClient(buffer);
        } finally {
            buffer.release();
        }
    }

    private static byte[] bytes(int value) {
        byte[] result = new byte[RenderProtocol.PROOF_BYTES];
        java.util.Arrays.fill(result, (byte) value);
        return result;
    }

    private static UUID uuid(int value) {
        return new UUID(0, value);
    }
}
