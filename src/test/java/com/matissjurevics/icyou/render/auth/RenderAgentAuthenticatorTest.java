package com.matissjurevics.icyou.render.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.protocol.RenderProtocol;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AgentHello;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthOutcome;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthProof;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;

class RenderAgentAuthenticatorTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void acceptsMatchingAllowlistedUuidAndSecret() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID player = UUID.randomUUID();
        var issued = store.issue(player);
        RenderAgentAuthenticator auth = authenticator(store);
        var challenge = auth.begin(player, hello(issued.credentialId()), START);
        byte[] proof = RenderAgentProofs.createProof(issued.token(), challenge.challengeId(),
                challenge.nonce(), player).orElseThrow();

        var completion = auth.complete(player,
                new AuthProof(challenge.challengeId(), proof), START.plusSeconds(1));

        assertEquals(AuthOutcome.ACCEPTED, completion.response().outcome());
        assertTrue(completion.session().isPresent());
        assertTrue(auth.isAuthenticated(player));
        assertEquals(2, completion.session().orElseThrow().capacity());
    }

    @Test
    void rejectsWrongUuidOrSecretWithoutRevealingCredentialState() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID allowed = UUID.randomUUID();
        UUID wrongPlayer = UUID.randomUUID();
        var issued = store.issue(allowed);
        var other = store.issue(UUID.randomUUID());
        RenderAgentAuthenticator auth = authenticator(store);

        var wrongUuidChallenge = auth.begin(wrongPlayer, hello(issued.credentialId()), START);
        byte[] wrongUuidProof = RenderAgentProofs.createProof(issued.token(),
                wrongUuidChallenge.challengeId(), wrongUuidChallenge.nonce(), wrongPlayer)
                .orElseThrow();
        var wrongUuid = auth.complete(wrongPlayer,
                new AuthProof(wrongUuidChallenge.challengeId(), wrongUuidProof),
                START.plusSeconds(1));

        var wrongSecretChallenge = auth.begin(allowed, hello(issued.credentialId()), START);
        byte[] wrongSecretProof = RenderAgentProofs.createProof(other.token(),
                wrongSecretChallenge.challengeId(), wrongSecretChallenge.nonce(), allowed)
                .orElseThrow();
        var wrongSecret = auth.complete(allowed,
                new AuthProof(wrongSecretChallenge.challengeId(), wrongSecretProof),
                START.plusSeconds(1));

        assertEquals(AuthOutcome.DENIED, wrongUuid.response().outcome());
        assertEquals(AuthOutcome.DENIED, wrongSecret.response().outcome());
        assertTrue(wrongUuid.response().sessionId().isEmpty());
        assertTrue(wrongSecret.response().sessionId().isEmpty());
    }

    @Test
    void challengeExpiresAndFirstAttemptConsumesIt() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID player = UUID.randomUUID();
        var issued = store.issue(player);
        RenderAgentAuthenticator auth = authenticator(store);
        var challenge = auth.begin(player, hello(issued.credentialId()), START);
        byte[] valid = RenderAgentProofs.createProof(issued.token(), challenge.challengeId(),
                challenge.nonce(), player).orElseThrow();

        var expired = auth.complete(player, new AuthProof(challenge.challengeId(), valid),
                START.plus(RenderAgentAuthenticator.CHALLENGE_TIMEOUT));
        var replay = auth.complete(player, new AuthProof(challenge.challengeId(), valid),
                START.plusSeconds(1));

        assertEquals(AuthOutcome.DENIED, expired.response().outcome());
        assertEquals(AuthOutcome.DENIED, replay.response().outcome());
        assertFalse(auth.isAuthenticated(player));
    }

    @Test
    void failedReauthenticationRemovesPriorSession() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID player = UUID.randomUUID();
        var issued = store.issue(player);
        RenderAgentAuthenticator auth = authenticator(store);
        authenticate(auth, issued, player);
        assertTrue(auth.isAuthenticated(player));

        var challenge = auth.begin(player, hello(issued.credentialId()), START.plusSeconds(2));
        auth.complete(player, new AuthProof(challenge.challengeId(), new byte[32]),
                START.plusSeconds(3));

        assertFalse(auth.isAuthenticated(player));
    }

    @Test
    void revocationAndDisconnectRemoveTransientSessions() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID player = UUID.randomUUID();
        var issued = store.issue(player);
        RenderAgentAuthenticator auth = authenticator(store);
        authenticate(auth, issued, player);

        assertEquals(Set.of(player), auth.revokeCredential(issued.credentialId()));
        assertFalse(auth.isAuthenticated(player));

        authenticate(auth, issued, player);
        auth.disconnect(player);
        assertFalse(auth.isAuthenticated(player));
    }

    private static void authenticate(RenderAgentAuthenticator auth,
                                     RenderAgentCredentialStore.IssuedCredential issued,
                                     UUID player) {
        var challenge = auth.begin(player, hello(issued.credentialId()), START);
        byte[] proof = RenderAgentProofs.createProof(issued.token(), challenge.challengeId(),
                challenge.nonce(), player).orElseThrow();
        auth.complete(player, new AuthProof(challenge.challengeId(), proof),
                START.plusSeconds(1));
    }

    private static AgentHello hello(UUID credentialId) {
        return new AgentHello(credentialId, 2, Set.of(MediaTransport.WEBRTC));
    }

    private static RenderAgentAuthenticator authenticator(
            RenderAgentCredentialStore store) {
        return new RenderAgentAuthenticator(store, new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 7);
            }
        });
    }
}
