package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.matissjurevics.icyou.render.auth.RenderAgentProofs;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;

class RenderAgentConfigTest {

    @TempDir
    Path directory;

    @Test
    void missingConfigLeavesAgentDisabled() {
        var loaded = RenderAgentConfig.load(directory.resolve("missing.properties"));

        assertTrue(loaded.valid());
        assertFalse(loaded.settings().enabled());
        assertTrue(loaded.settings().credential().isEmpty());
    }

    @Test
    void enabledConfigParsesCredentialCapacityAndTransports() throws IOException {
        UUID credentialId = UUID.randomUUID();
        Path path = write("agent.enabled=true\nagent.token=" + token(credentialId)
                + "\nagent.capacity=3\nagent.transports=mjpeg, WEBRTC\n");

        var loaded = RenderAgentConfig.load(path);

        assertTrue(loaded.valid());
        assertTrue(loaded.settings().enabled());
        assertEquals(credentialId,
                loaded.settings().credential().orElseThrow().credentialId());
        assertEquals(3, loaded.settings().capacity());
        assertEquals(Set.of(MediaTransport.MJPEG, MediaTransport.WEBRTC),
                loaded.settings().transports());
    }

    @Test
    void malformedEnabledSettingsFailClosed() throws IOException {
        assertInvalid("agent.enabled=true\nagent.token=secret\n");
        assertInvalid("agent.enabled=true\nagent.token=" + token(UUID.randomUUID())
                + "\nagent.capacity=5\n");
        assertInvalid("agent.enabled=true\nagent.token=" + token(UUID.randomUUID())
                + "\nagent.transports=unknown\n");
        assertInvalid("agent.enabled=yes\n");
    }

    @Test
    void disabledConfigDoesNotRequireOrParseASecret() throws IOException {
        var loaded = RenderAgentConfig.load(write(
                "agent.enabled=false\nagent.token=not-a-token\nagent.capacity=999\n"));

        assertTrue(loaded.valid());
        assertFalse(loaded.settings().enabled());
    }

    private void assertInvalid(String properties) throws IOException {
        var loaded = RenderAgentConfig.load(write(properties));
        assertFalse(loaded.valid());
        assertFalse(loaded.settings().enabled());
        assertTrue(loaded.error().isPresent());
    }

    private Path write(String value) throws IOException {
        Path path = directory.resolve(UUID.randomUUID() + ".properties");
        Files.writeString(path, value);
        return path;
    }

    private static String token(UUID credentialId) {
        byte[] secret = new byte[32];
        java.util.Arrays.fill(secret, (byte) 7);
        return RenderAgentProofs.TOKEN_PREFIX + credentialId + '_'
                + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
