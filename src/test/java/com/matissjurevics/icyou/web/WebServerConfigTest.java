package com.matissjurevics.icyou.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebServerConfigTest {

    @Test
    void missingConfigurationIsDisabledAndLoopbackOnly(@TempDir Path temp) throws IOException {
        WebServerConfig config = WebServerConfig.load(temp.resolve("missing.properties"));

        assertFalse(config.enabled());
        assertEquals("127.0.0.1", config.bind());
        assertEquals(8123, config.port());
    }

    @Test
    void readsExplicitOptInConfiguration(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("icyou-web.properties");
        Files.writeString(file, "web.enabled=true\nweb.bind=localhost\nweb.port=9001\n");

        WebServerConfig config = WebServerConfig.load(file);

        assertTrue(config.enabled());
        assertEquals("localhost", config.bind());
        assertEquals(9001, config.port());
    }

    @Test
    void rejectsInvalidPorts(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("icyou-web.properties");
        Files.writeString(file, "web.enabled=true\nweb.port=not-a-port\n");
        assertThrows(IOException.class, () -> WebServerConfig.load(file));

        Files.writeString(file, "web.enabled=true\nweb.port=70000\n");
        assertThrows(IOException.class, () -> WebServerConfig.load(file));
    }
}
