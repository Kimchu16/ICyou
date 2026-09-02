package com.matissjurevics.icyou.web.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Server-persisted, terminal-scoped credentials. Plaintext tokens are never saved. */
public final class TerminalCredentialStore extends PersistentState {

    public static final String PERSISTENCE_KEY = "icyou_terminal_credentials";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Type<TerminalCredentialStore> TYPE = new Type<>(
            TerminalCredentialStore::new, TerminalCredentialStore::readNbt, null);

    public enum Scope { VIEWER, OWNER }

    public record IssuedToken(UUID credentialId, UUID terminalId, Scope scope, String token) {
    }

    public record AuthenticatedCredential(UUID credentialId, UUID terminalId, Scope scope) {
    }

    private record Credential(UUID credentialId, UUID terminalId, Scope scope,
                              byte[] digest, Instant createdAt) {
        private Credential {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(terminalId, "terminalId");
            Objects.requireNonNull(scope, "scope");
            digest = Objects.requireNonNull(digest, "digest").clone();
            Objects.requireNonNull(createdAt, "createdAt");
        }

        @Override
        public byte[] digest() {
            return digest.clone();
        }
    }

    private final Map<UUID, Credential> credentials = new LinkedHashMap<>();

    public static TerminalCredentialStore get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(TYPE, PERSISTENCE_KEY);
    }

    public IssuedToken issue(UUID terminalId, Scope scope) {
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(scope, "scope");
        UUID credentialId = UUID.randomUUID();
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String token = "icyou_" + credentialId + '_' + encodedSecret;
        credentials.put(credentialId, new Credential(credentialId, terminalId, scope,
                digest(token), Instant.now()));
        markDirty();
        return new IssuedToken(credentialId, terminalId, scope, token);
    }

    public Optional<Scope> authenticate(UUID terminalId, String token) {
        return authenticate(token).filter(credential ->
                credential.terminalId().equals(terminalId)).map(AuthenticatedCredential::scope);
    }

    public Optional<AuthenticatedCredential> authenticate(String token) {
        ParsedToken parsed = parse(token);
        if (parsed == null) {
            return Optional.empty();
        }
        Credential credential = credentials.get(parsed.credentialId());
        if (credential == null
                || !MessageDigest.isEqual(credential.digest(), digest(token))) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedCredential(credential.credentialId(),
                credential.terminalId(), credential.scope()));
    }

    public boolean permits(UUID terminalId, String token, Scope required) {
        return authenticate(terminalId, token).filter(actual -> actual == Scope.OWNER
                || actual == required).isPresent();
    }

    public boolean revoke(UUID terminalId, UUID credentialId) {
        Credential credential = credentials.get(credentialId);
        if (credential == null || !credential.terminalId().equals(terminalId)) {
            return false;
        }
        credentials.remove(credentialId);
        markDirty();
        return true;
    }

    public int revokeAll(UUID terminalId, Scope scope) {
        int before = credentials.size();
        credentials.values().removeIf(credential -> credential.terminalId().equals(terminalId)
                && credential.scope() == scope);
        int removed = before - credentials.size();
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("schemaVersion", CameraOverhaulContracts.SAVE_SCHEMA_VERSION);
        NbtList list = new NbtList();
        for (Credential credential : credentials.values()) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("credentialId", credential.credentialId());
            tag.putUuid("terminalId", credential.terminalId());
            tag.putString("scope", credential.scope().name());
            tag.putByteArray("digest", credential.digest());
            tag.putLong("createdAt", credential.createdAt().toEpochMilli());
            list.add(tag);
        }
        nbt.put("credentials", list);
        return nbt;
    }

    static TerminalCredentialStore readNbt(NbtCompound nbt,
                                           RegistryWrapper.WrapperLookup lookup) {
        TerminalCredentialStore store = new TerminalCredentialStore();
        int schemaVersion = nbt.getInt("schemaVersion");
        if (schemaVersion != CameraOverhaulContracts.SAVE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported terminal credential schema: " + schemaVersion);
        }
        NbtList list = nbt.getList("credentials", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            UUID credentialId = tag.getUuid("credentialId");
            store.credentials.put(credentialId, new Credential(
                    credentialId, tag.getUuid("terminalId"),
                    Scope.valueOf(tag.getString("scope")), tag.getByteArray("digest"),
                    Instant.ofEpochMilli(tag.getLong("createdAt"))));
        }
        store.setDirty(false);
        return store;
    }

    private static ParsedToken parse(String token) {
        if (token == null || !token.startsWith("icyou_") || token.length() > 128) {
            return null;
        }
        int separator = token.indexOf('_', 6);
        if (separator < 0 || separator == token.length() - 1) {
            return null;
        }
        try {
            UUID credentialId = UUID.fromString(token.substring(6, separator));
            byte[] secret = Base64.getUrlDecoder().decode(token.substring(separator + 1));
            return secret.length == 32 ? new ParsedToken(credentialId) : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static byte[] digest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record ParsedToken(UUID credentialId) {
    }
}
