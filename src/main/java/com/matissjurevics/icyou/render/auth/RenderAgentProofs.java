package com.matissjurevics.icyou.render.auth;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.matissjurevics.icyou.render.protocol.RenderProtocol;

/** Token parsing and canonical HMAC proof generation shared with PR 16's agent. */
public final class RenderAgentProofs {

    public static final String TOKEN_PREFIX = "icyou_render_";
    private static final int SECRET_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 160;

    public record TokenMaterial(UUID credentialId, byte[] key) {
        public TokenMaterial {
            Objects.requireNonNull(credentialId, "credentialId");
            key = requireLength(key, "key");
        }

        @Override
        public byte[] key() {
            return key.clone();
        }
    }

    private RenderAgentProofs() {
    }

    public static Optional<TokenMaterial> parse(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)
                || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        int separator = token.indexOf('_', TOKEN_PREFIX.length());
        if (separator < 0 || separator == token.length() - 1) {
            return Optional.empty();
        }
        try {
            UUID credentialId = UUID.fromString(
                    token.substring(TOKEN_PREFIX.length(), separator));
            byte[] secret = Base64.getUrlDecoder().decode(token.substring(separator + 1));
            if (secret.length != SECRET_BYTES) {
                return Optional.empty();
            }
            return Optional.of(new TokenMaterial(credentialId, sha256(secret)));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    public static Optional<byte[]> createProof(String token, UUID challengeId, byte[] nonce,
                                               UUID minecraftId) {
        return parse(token).map(material -> sign(material.key(), challengeId, nonce, minecraftId));
    }

    static byte[] sign(byte[] key, UUID challengeId, byte[] nonce, UUID minecraftId) {
        byte[] keyCopy = requireLength(key, "key");
        Objects.requireNonNull(challengeId, "challengeId");
        byte[] nonceCopy = requireLength(nonce, "nonce");
        Objects.requireNonNull(minecraftId, "minecraftId");
        ByteBuffer input = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 4
                + RenderProtocol.PROOF_BYTES);
        input.putInt(RenderProtocol.VERSION);
        putUuid(input, challengeId);
        input.put(nonceCopy);
        putUuid(input, minecraftId);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyCopy, "HmacSHA256"));
            return mac.doFinal(input.array());
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 is unavailable", impossible);
        }
    }

    static byte[] sha256(byte[] secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void putUuid(ByteBuffer buffer, UUID value) {
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
    }

    private static byte[] requireLength(byte[] value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length != RenderProtocol.PROOF_BYTES) {
            throw new IllegalArgumentException(label + " must contain "
                    + RenderProtocol.PROOF_BYTES + " bytes");
        }
        return value.clone();
    }
}
