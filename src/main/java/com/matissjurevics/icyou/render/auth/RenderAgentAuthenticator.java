package com.matissjurevics.icyou.render.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.render.protocol.RenderProtocol;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AgentHello;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthChallenge;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthOutcome;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthProof;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthResult;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;

/** Single-use challenge authentication and transient render-agent sessions. */
public final class RenderAgentAuthenticator {

    public static final Duration CHALLENGE_TIMEOUT = Duration.ofSeconds(15);

    public record Session(UUID sessionId, UUID minecraftId, UUID credentialId,
                          int capacity, Set<MediaTransport> transports,
                          Instant authenticatedAt) {
        public Session {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(minecraftId, "minecraftId");
            Objects.requireNonNull(credentialId, "credentialId");
            transports = Set.copyOf(Objects.requireNonNull(transports, "transports"));
            Objects.requireNonNull(authenticatedAt, "authenticatedAt");
        }
    }

    public record Completion(AuthResult response, Optional<Session> session) {
        public Completion {
            Objects.requireNonNull(response, "response");
            session = Objects.requireNonNull(session, "session");
        }
    }

    private record Pending(UUID challengeId, byte[] nonce, UUID credentialId,
                           AgentHello hello, Instant expiresAt) {
        private Pending {
            nonce = nonce.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }
    }

    private final RenderAgentCredentialStore credentials;
    private final SecureRandom random;
    private final Map<UUID, Pending> pendingByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Session> sessionsByPlayer = new LinkedHashMap<>();

    public RenderAgentAuthenticator(RenderAgentCredentialStore credentials) {
        this(credentials, new SecureRandom());
    }

    RenderAgentAuthenticator(RenderAgentCredentialStore credentials, SecureRandom random) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized AuthChallenge begin(UUID minecraftId, AgentHello hello, Instant now) {
        Objects.requireNonNull(minecraftId, "minecraftId");
        Objects.requireNonNull(hello, "hello");
        Objects.requireNonNull(now, "now");
        expire(now);
        sessionsByPlayer.remove(minecraftId);
        byte[] nonce = new byte[RenderProtocol.PROOF_BYTES];
        random.nextBytes(nonce);
        Pending pending = new Pending(UUID.randomUUID(), nonce, hello.credentialId(), hello,
                now.plus(CHALLENGE_TIMEOUT));
        pendingByPlayer.put(minecraftId, pending);
        return new AuthChallenge(pending.challengeId(), pending.nonce());
    }

    public synchronized Completion complete(UUID minecraftId, AuthProof proof, Instant now) {
        Objects.requireNonNull(minecraftId, "minecraftId");
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(now, "now");
        Pending pending = pendingByPlayer.remove(minecraftId);
        if (pending == null || !pending.challengeId().equals(proof.challengeId())
                || !now.isBefore(pending.expiresAt())) {
            return denied(proof.challengeId());
        }
        Optional<byte[]> key = credentials.key(pending.credentialId(), minecraftId);
        if (key.isEmpty()) {
            return denied(pending.challengeId());
        }
        byte[] expected = RenderAgentProofs.sign(key.orElseThrow(), pending.challengeId(),
                pending.nonce(), minecraftId);
        if (!MessageDigest.isEqual(expected, proof.proof())) {
            return denied(pending.challengeId());
        }
        Session session = new Session(UUID.randomUUID(), minecraftId, pending.credentialId(),
                pending.hello().capacity(), pending.hello().transports(), now);
        sessionsByPlayer.put(minecraftId, session);
        return new Completion(new AuthResult(pending.challengeId(), AuthOutcome.ACCEPTED,
                Optional.of(session.sessionId())), Optional.of(session));
    }

    public synchronized boolean isAuthenticated(UUID minecraftId) {
        return sessionsByPlayer.containsKey(minecraftId);
    }

    public synchronized Optional<Session> session(UUID minecraftId) {
        return Optional.ofNullable(sessionsByPlayer.get(minecraftId));
    }

    public synchronized int sessionCount() {
        return sessionsByPlayer.size();
    }

    public synchronized Set<UUID> revokeCredential(UUID credentialId) {
        Set<UUID> affected = sessionsByPlayer.values().stream()
                .filter(session -> session.credentialId().equals(credentialId))
                .map(Session::minecraftId).collect(java.util.stream.Collectors.toSet());
        sessionsByPlayer.values().removeIf(session ->
                session.credentialId().equals(credentialId));
        pendingByPlayer.values().removeIf(pending ->
                pending.credentialId().equals(credentialId));
        return Set.copyOf(affected);
    }

    public synchronized void disconnect(UUID minecraftId) {
        pendingByPlayer.remove(minecraftId);
        sessionsByPlayer.remove(minecraftId);
    }

    public synchronized int expire(Instant now) {
        Objects.requireNonNull(now, "now");
        int before = pendingByPlayer.size();
        pendingByPlayer.values().removeIf(pending -> !now.isBefore(pending.expiresAt()));
        return before - pendingByPlayer.size();
    }

    public synchronized void clear() {
        pendingByPlayer.clear();
        sessionsByPlayer.clear();
    }

    private static Completion denied(UUID challengeId) {
        return new Completion(new AuthResult(challengeId, AuthOutcome.DENIED, Optional.empty()),
                Optional.empty());
    }
}
