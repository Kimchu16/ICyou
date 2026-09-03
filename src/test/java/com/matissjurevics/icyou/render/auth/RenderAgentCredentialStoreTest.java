package com.matissjurevics.icyou.render.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NbtCompound;

class RenderAgentCredentialStoreTest {

    @Test
    void issuedCredentialBindsSecretToMinecraftUuid() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID player = UUID.randomUUID();
        var issued = store.issue(player);
        var material = RenderAgentProofs.parse(issued.token()).orElseThrow();

        assertEquals(issued.credentialId(), material.credentialId());
        assertArrayEquals(material.key(),
                store.key(issued.credentialId(), player).orElseThrow());
        assertTrue(store.key(issued.credentialId(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void saveContainsDigestButNotTokenOrRawSecret() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID player = UUID.randomUUID();
        var issued = store.issue(player);
        String encodedSecret = issued.token().substring(issued.token().lastIndexOf('_') + 1);

        NbtCompound saved = store.writeNbt(new NbtCompound(), null);

        assertFalse(saved.toString().contains(issued.token()));
        assertFalse(saved.toString().contains(encodedSecret));
        RenderAgentCredentialStore restored = RenderAgentCredentialStore.readNbt(saved, null);
        assertTrue(restored.key(issued.credentialId(), player).isPresent());
        assertFalse(restored.isDirty());
    }

    @Test
    void revokesOneCredentialOrEveryCredentialForPlayer() {
        RenderAgentCredentialStore store = new RenderAgentCredentialStore();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        var first = store.issue(firstPlayer);
        store.issue(firstPlayer);
        var other = store.issue(secondPlayer);

        assertTrue(store.revoke(first.credentialId()));
        assertFalse(store.revoke(first.credentialId()));
        assertEquals(1, store.revokeAll(firstPlayer));
        assertTrue(store.key(other.credentialId(), secondPlayer).isPresent());
    }

    @Test
    void malformedTokensAndFutureSaveSchemasFailClosed() {
        assertTrue(RenderAgentProofs.parse(null).isEmpty());
        assertTrue(RenderAgentProofs.parse("").isEmpty());
        assertTrue(RenderAgentProofs.parse("icyou_render_bad_secret").isEmpty());
        assertTrue(RenderAgentProofs.parse("icyou_render_" + UUID.randomUUID()
                + "_tiny").isEmpty());

        NbtCompound saved = new NbtCompound();
        saved.putInt("schemaVersion", 999);
        assertThrows(IllegalArgumentException.class,
                () -> RenderAgentCredentialStore.readNbt(saved, null));
    }
}
