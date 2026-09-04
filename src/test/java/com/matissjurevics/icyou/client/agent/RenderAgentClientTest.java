package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.auth.RenderAgentProofs;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthChallenge;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthOutcome;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthProof;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthResult;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.CancelReason;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.ClientMessage;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobCancel;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobState;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobStatus;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class RenderAgentClientTest {

    @Test
    void handshakeUsesCredentialWithoutRetainingPlaintextToken() {
        Fixture fixture = fixture(2);
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        byte[] nonce = new byte[32];
        java.util.Arrays.fill(nonce, (byte) 4);

        assertTrue(fixture.client.connect(playerId));
        assertEquals(fixture.credentialId,
                ((com.matissjurevics.icyou.render.protocol.RenderProtocol.AgentHello)
                        fixture.messages.get(0)).credentialId());
        assertTrue(fixture.client.challenge(new AuthChallenge(challengeId, nonce)));

        AuthProof proof = (AuthProof) fixture.messages.get(1);
        assertArrayEquals(RenderAgentProofs.createProof(fixture.token, challengeId,
                nonce, playerId).orElseThrow(), proof.proof());
        UUID session = UUID.randomUUID();
        assertTrue(fixture.client.authentication(new AuthResult(challengeId,
                AuthOutcome.ACCEPTED, Optional.of(session))));
        assertEquals(RenderAgentClient.State.AUTHENTICATED, fixture.client.state());
        assertEquals(Optional.of(session), fixture.client.sessionId());
    }

    @Test
    void wrongChallengeResultCannotAuthenticate() {
        Fixture fixture = fixture(1);
        fixture.client.connect(UUID.randomUUID());
        UUID challenge = UUID.randomUUID();
        fixture.client.challenge(new AuthChallenge(challenge, new byte[32]));

        assertFalse(fixture.client.authentication(new AuthResult(UUID.randomUUID(),
                AuthOutcome.ACCEPTED, Optional.of(UUID.randomUUID()))));
        assertEquals(RenderAgentClient.State.PROOF_SENT, fixture.client.state());
    }

    @Test
    void disabledAgentNeverStartsAHandshake() {
        List<ClientMessage> messages = new ArrayList<>();
        RenderAgentClient client = new RenderAgentClient(
                RenderAgentConfig.Settings.disabled(), messages::add,
                new ClientRenderJobExecutor());

        assertFalse(client.connect(UUID.randomUUID()));
        assertTrue(messages.isEmpty());
        assertEquals(RenderAgentClient.State.DISCONNECTED, client.state());
    }

    @Test
    void acceptsSameDimensionJobsAndEnforcesCapacity() {
        Fixture fixture = authenticated(1);
        JobAssignment first = job(World.OVERWORLD, 0);
        JobAssignment second = job(World.OVERWORLD, 0);

        assertTrue(fixture.client.assign(first, World.OVERWORLD));
        assertFalse(fixture.client.assign(second, World.OVERWORLD));

        assertEquals(1, fixture.executor.jobs().size());
        assertEquals(JobState.ACCEPTED, fixture.lastStatus(2).state());
        assertEquals(JobState.FAILED, fixture.lastStatus(3).state());
    }

    @Test
    void rejectsCrossDimensionJobWithoutStartingIt() {
        Fixture fixture = authenticated(2);
        JobAssignment job = job(World.NETHER, 0);

        assertFalse(fixture.client.assign(job, World.OVERWORLD));

        assertTrue(fixture.executor.jobs().isEmpty());
        assertEquals(JobState.FAILED, fixture.lastStatus(2).state());
    }

    @Test
    void cancellationMustAdvanceTheKnownRevision() {
        Fixture fixture = authenticated(1);
        JobAssignment job = job(World.OVERWORLD, 3);
        fixture.client.assign(job, World.OVERWORLD);

        assertFalse(fixture.client.cancel(new JobCancel(job.jobId(), 3,
                CancelReason.REASSIGNED)));
        assertTrue(fixture.client.cancel(new JobCancel(job.jobId(), 4,
                CancelReason.REASSIGNED)));
        assertFalse(fixture.client.assign(job, World.OVERWORLD));

        assertTrue(fixture.client.activeJobs().isEmpty());
        assertTrue(fixture.executor.jobs().isEmpty());
    }

    @Test
    void invalidNewRevisionStillReplacesTheOldLocalJob() {
        Fixture fixture = authenticated(1);
        JobAssignment original = job(World.OVERWORLD, 0);
        fixture.client.assign(original, World.OVERWORLD);
        JobAssignment replacement = new JobAssignment(original.jobId(), 1,
                new CameraRef(UUID.randomUUID(), World.NETHER, new BlockPos(4, 70, 5)),
                CameraOverhaulContracts.VIDEO_WIDTH, CameraOverhaulContracts.VIDEO_HEIGHT,
                CameraOverhaulContracts.VIDEO_FPS);

        assertFalse(fixture.client.assign(replacement, World.OVERWORLD));

        assertTrue(fixture.client.activeJobs().isEmpty());
        assertTrue(fixture.executor.jobs().isEmpty());
        assertEquals(JobState.FAILED, fixture.lastStatus(3).state());
    }

    @Test
    void executorAloneControlsAvailableAndFailedReports() {
        Fixture fixture = authenticated(2);
        JobAssignment available = job(World.OVERWORLD, 0);
        JobAssignment failed = job(World.OVERWORLD, 0);
        fixture.client.assign(available, World.OVERWORLD);
        fixture.client.assign(failed, World.OVERWORLD);

        assertTrue(fixture.client.markAvailable(available.jobId()));
        assertTrue(fixture.client.markFailed(failed.jobId(), "scene\nfailed" + "x".repeat(200)));

        assertEquals(JobState.AVAILABLE, fixture.lastStatus(4).state());
        assertEquals(JobState.FAILED, fixture.lastStatus(5).state());
        assertFalse(fixture.lastStatus(5).detail().contains("\n"));
        assertEquals(160, fixture.lastStatus(5).detail().length());
        assertTrue(fixture.client.activeJobs().containsKey(available.jobId()));
        assertFalse(fixture.client.activeJobs().containsKey(failed.jobId()));
    }

    @Test
    void disconnectClearsSessionAndEveryJob() {
        Fixture fixture = authenticated(1);
        fixture.client.assign(job(World.OVERWORLD, 0), World.OVERWORLD);

        fixture.client.disconnect();

        assertEquals(RenderAgentClient.State.DISCONNECTED, fixture.client.state());
        assertTrue(fixture.client.sessionId().isEmpty());
        assertTrue(fixture.client.activeJobs().isEmpty());
        assertTrue(fixture.executor.jobs().isEmpty());
    }

    private static Fixture authenticated(int capacity) {
        Fixture fixture = fixture(capacity);
        UUID player = UUID.randomUUID();
        UUID challenge = UUID.randomUUID();
        fixture.client.connect(player);
        fixture.client.challenge(new AuthChallenge(challenge, new byte[32]));
        fixture.client.authentication(new AuthResult(challenge, AuthOutcome.ACCEPTED,
                Optional.of(UUID.randomUUID())));
        return fixture;
    }

    private static Fixture fixture(int capacity) {
        UUID credentialId = UUID.randomUUID();
        String token = token(credentialId);
        var settings = new RenderAgentConfig.Settings(true,
                RenderAgentProofs.parse(token), capacity, Set.of(MediaTransport.MJPEG));
        List<ClientMessage> messages = new ArrayList<>();
        ClientRenderJobExecutor executor = new ClientRenderJobExecutor();
        return new Fixture(new RenderAgentClient(settings, messages::add, executor),
                executor, messages, credentialId, token);
    }

    private static JobAssignment job(net.minecraft.registry.RegistryKey<World> dimension,
                                     long revision) {
        return new JobAssignment(UUID.randomUUID(), revision,
                new CameraRef(UUID.randomUUID(), dimension, new BlockPos(1, 70, 2)),
                CameraOverhaulContracts.VIDEO_WIDTH, CameraOverhaulContracts.VIDEO_HEIGHT,
                CameraOverhaulContracts.VIDEO_FPS);
    }

    private static String token(UUID credentialId) {
        byte[] secret = new byte[32];
        java.util.Arrays.fill(secret, (byte) 9);
        return RenderAgentProofs.TOKEN_PREFIX + credentialId + '_'
                + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private record Fixture(RenderAgentClient client, ClientRenderJobExecutor executor,
                           List<ClientMessage> messages, UUID credentialId, String token) {
        private JobStatus lastStatus(int index) {
            return (JobStatus) messages.get(index);
        }
    }
}
