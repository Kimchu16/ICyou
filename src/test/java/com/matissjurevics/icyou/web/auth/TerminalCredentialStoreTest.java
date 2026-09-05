package com.matissjurevics.icyou.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NbtCompound;

class TerminalCredentialStoreTest {

    @Test
    void issuesSeparateTerminalScopedViewerAndOwnerTokens() {
        TerminalCredentialStore store = new TerminalCredentialStore();
        UUID firstTerminal = UUID.randomUUID();
        UUID secondTerminal = UUID.randomUUID();
        var viewer = store.issue(firstTerminal, TerminalCredentialStore.Scope.VIEWER);
        var owner = store.issue(firstTerminal, TerminalCredentialStore.Scope.OWNER);

        assertNotEquals(viewer.token(), owner.token());
        assertTrue(store.permits(firstTerminal, viewer.token(),
                TerminalCredentialStore.Scope.VIEWER));
        assertFalse(store.permits(firstTerminal, viewer.token(),
                TerminalCredentialStore.Scope.OWNER));
        assertTrue(store.permits(firstTerminal, owner.token(),
                TerminalCredentialStore.Scope.VIEWER));
        assertTrue(store.permits(firstTerminal, owner.token(),
                TerminalCredentialStore.Scope.OWNER));
        assertFalse(store.permits(secondTerminal, owner.token(),
                TerminalCredentialStore.Scope.VIEWER));
        assertFalse(store.permits(firstTerminal, owner.token() + "x",
                TerminalCredentialStore.Scope.VIEWER));
    }

    @Test
    void revokesOneTokenOrOneScopeWithoutAffectingOthers() {
        TerminalCredentialStore store = new TerminalCredentialStore();
        UUID terminal = UUID.randomUUID();
        var firstViewer = store.issue(terminal, TerminalCredentialStore.Scope.VIEWER);
        var secondViewer = store.issue(terminal, TerminalCredentialStore.Scope.VIEWER);
        var owner = store.issue(terminal, TerminalCredentialStore.Scope.OWNER);

        assertTrue(store.revoke(terminal, firstViewer.credentialId()));
        assertFalse(store.authenticate(terminal, firstViewer.token()).isPresent());
        assertTrue(store.authenticate(terminal, secondViewer.token()).isPresent());
        assertEquals(1, store.revokeAll(terminal, TerminalCredentialStore.Scope.VIEWER));
        assertFalse(store.authenticate(terminal, secondViewer.token()).isPresent());
        assertTrue(store.authenticate(terminal, owner.token()).isPresent());
    }

    @Test
    void persistenceStoresOnlyDigestsAndKeepsTokensValid() {
        TerminalCredentialStore store = new TerminalCredentialStore();
        UUID terminal = UUID.randomUUID();
        var issued = store.issue(terminal, TerminalCredentialStore.Scope.OWNER);

        NbtCompound saved = store.writeNbt(new NbtCompound(), null);
        String serialized = saved.toString();
        assertFalse(serialized.contains(issued.token()));
        assertFalse(serialized.contains(java.util.Base64.getEncoder().encodeToString(
                issued.token().getBytes(StandardCharsets.UTF_8))));

        TerminalCredentialStore restored = TerminalCredentialStore.readNbt(saved, null);
        assertTrue(restored.permits(terminal, issued.token(),
                TerminalCredentialStore.Scope.OWNER));
        assertFalse(restored.isDirty());

        saved.putInt("schemaVersion", 999);
        assertThrows(IllegalArgumentException.class,
                () -> TerminalCredentialStore.readNbt(saved, null));
    }

    @Test
    void malformedTokensFailClosed() {
        TerminalCredentialStore store = new TerminalCredentialStore();
        UUID terminal = UUID.randomUUID();

        assertTrue(store.authenticate(terminal, null).isEmpty());
        assertTrue(store.authenticate(terminal, "").isEmpty());
        assertTrue(store.authenticate(terminal, "icyou_not-a-uuid_secret").isEmpty());
        assertTrue(store.authenticate(terminal, "icyou_" + UUID.randomUUID() + "_tiny").isEmpty());
    }
}
