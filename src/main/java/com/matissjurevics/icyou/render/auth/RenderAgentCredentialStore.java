package com.matissjurevics.icyou.render.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.protocol.RenderProtocol;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Allowlisted Minecraft UUIDs and digest-only render-agent credentials. */
public final class RenderAgentCredentialStore extends PersistentState {

    public static final String PERSISTENCE_KEY = "icyou_render_agent_credentials";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Type<RenderAgentCredentialStore> TYPE = new Type<>(
            RenderAgentCredentialStore::new, RenderAgentCredentialStore::readNbt, null);

    public record IssuedCredential(UUID credentialId, UUID minecraftId, String token) {
    }

    private record Credential(UUID credentialId, UUID minecraftId, byte[] key,
                              Instant createdAt) {
        private Credential {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(minecraftId, "minecraftId");
            key = Objects.requireNonNull(key, "key").clone();
            if (key.length != RenderProtocol.PROOF_BYTES) {
                throw new IllegalArgumentException("Invalid render credential digest");
            }
            Objects.requireNonNull(createdAt, "createdAt");
        }

        @Override
        public byte[] key() {
            return key.clone();
        }
    }

    private final Map<UUID, Credential> credentials = new LinkedHashMap<>();

    public static RenderAgentCredentialStore get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(TYPE, PERSISTENCE_KEY);
    }

    public IssuedCredential issue(UUID minecraftId) {
        Objects.requireNonNull(minecraftId, "minecraftId");
        UUID credentialId = UUID.randomUUID();
        byte[] secret = new byte[RenderProtocol.PROOF_BYTES];
        RANDOM.nextBytes(secret);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        credentials.put(credentialId, new Credential(credentialId, minecraftId,
                RenderAgentProofs.sha256(secret), Instant.now()));
        markDirty();
        return new IssuedCredential(credentialId, minecraftId,
                RenderAgentProofs.TOKEN_PREFIX + credentialId + '_' + encoded);
    }

    public boolean revoke(UUID credentialId) {
        boolean removed = credentials.remove(Objects.requireNonNull(credentialId,
                "credentialId")) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public int revokeAll(UUID minecraftId) {
        int before = credentials.size();
        credentials.values().removeIf(credential ->
                credential.minecraftId().equals(minecraftId));
        int removed = before - credentials.size();
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    public List<UUID> credentialIds(UUID minecraftId) {
        return credentials.values().stream()
                .filter(credential -> credential.minecraftId().equals(minecraftId))
                .map(Credential::credentialId).toList();
    }

    Optional<byte[]> key(UUID credentialId, UUID minecraftId) {
        Credential credential = credentials.get(credentialId);
        return credential != null && credential.minecraftId().equals(minecraftId)
                ? Optional.of(credential.key()) : Optional.empty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("schemaVersion", CameraOverhaulContracts.SAVE_SCHEMA_VERSION);
        NbtList list = new NbtList();
        for (Credential credential : credentials.values()) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("credentialId", credential.credentialId());
            tag.putUuid("minecraftId", credential.minecraftId());
            tag.putByteArray("keyDigest", credential.key());
            tag.putLong("createdAt", credential.createdAt().toEpochMilli());
            list.add(tag);
        }
        nbt.put("credentials", list);
        return nbt;
    }

    static RenderAgentCredentialStore readNbt(NbtCompound nbt,
                                              RegistryWrapper.WrapperLookup lookup) {
        int version = nbt.getInt("schemaVersion");
        if (version != CameraOverhaulContracts.SAVE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported render credential schema: " + version);
        }
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        NbtList list = nbt.getList("credentials", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < list.size(); index++) {
            NbtCompound tag = list.getCompound(index);
            UUID credentialId = tag.getUuid("credentialId");
            store.credentials.put(credentialId, new Credential(credentialId,
                    tag.getUuid("minecraftId"), tag.getByteArray("keyDigest"),
                    Instant.ofEpochMilli(tag.getLong("createdAt"))));
        }
        store.setDirty(false);
        return store;
    }
}
