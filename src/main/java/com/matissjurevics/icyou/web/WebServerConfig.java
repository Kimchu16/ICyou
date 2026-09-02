package com.matissjurevics.icyou.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Safe, opt-in configuration for the logical server's web listener. */
public record WebServerConfig(boolean enabled, String bind, int port) {

    public static final WebServerConfig DISABLED = new WebServerConfig(
            false, "127.0.0.1", 8123);

    public WebServerConfig {
        bind = Objects.requireNonNull(bind, "bind").trim();
        if (bind.isEmpty()) {
            throw new IllegalArgumentException("Web bind address must not be empty");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Web port must be between 0 and 65535");
        }
    }

    public static WebServerConfig load(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file)) {
            return DISABLED;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        boolean enabled = Boolean.parseBoolean(
                properties.getProperty("web.enabled", "false"));
        String bind = properties.getProperty("web.bind", "127.0.0.1");
        int port;
        try {
            port = Integer.parseInt(properties.getProperty("web.port", "8123"));
        } catch (NumberFormatException error) {
            throw new IOException("Invalid web.port", error);
        }
        try {
            return new WebServerConfig(enabled, bind, port);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid ICyou web configuration", error);
        }
    }
}
