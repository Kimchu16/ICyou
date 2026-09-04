package com.matissjurevics.icyou.client.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.auth.RenderAgentProofs;
import com.matissjurevics.icyou.render.auth.RenderAgentProofs.TokenMaterial;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;

/** Loads opt-in render-agent settings without retaining the plaintext token. */
public final class RenderAgentConfig {

    public static final String FILE_NAME = "icyou-render-agent.properties";

    public record Settings(boolean enabled, Optional<TokenMaterial> credential,
                           int capacity, Set<MediaTransport> transports) {
        public Settings {
            credential = Objects.requireNonNull(credential, "credential");
            transports = Set.copyOf(Objects.requireNonNull(transports, "transports"));
            if (capacity < 1 || capacity > CameraOverhaulContracts.MAX_ACTIVE_CAMERAS) {
                throw new IllegalArgumentException("Render-agent capacity must be between 1 and "
                        + CameraOverhaulContracts.MAX_ACTIVE_CAMERAS);
            }
            if (enabled && (credential.isEmpty() || transports.isEmpty())) {
                throw new IllegalArgumentException(
                        "Enabled render agent requires a credential and transport");
            }
        }

        public static Settings disabled() {
            return new Settings(false, Optional.empty(), 1, Set.of());
        }
    }

    public record LoadResult(Settings settings, Optional<String> error) {
        public LoadResult {
            Objects.requireNonNull(settings, "settings");
            error = Objects.requireNonNull(error, "error");
        }

        public boolean valid() {
            return error.isEmpty();
        }
    }

    private RenderAgentConfig() {
    }

    public static LoadResult load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return new LoadResult(Settings.disabled(), Optional.empty());
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException error) {
            return invalid("could not read the configuration file");
        }
        String enabled = properties.getProperty("agent.enabled", "false").trim();
        if (enabled.equalsIgnoreCase("false")) {
            return new LoadResult(Settings.disabled(), Optional.empty());
        }
        if (!enabled.equalsIgnoreCase("true")) {
            return invalid("agent.enabled must be true or false");
        }
        try {
            int capacity = Integer.parseInt(properties.getProperty("agent.capacity", "1"));
            Optional<TokenMaterial> credential = RenderAgentProofs.parse(
                    properties.getProperty("agent.token", "").trim());
            if (credential.isEmpty()) {
                return invalid("agent.token is missing or invalid");
            }
            Set<MediaTransport> transports = parseTransports(
                    properties.getProperty("agent.transports", "mjpeg"));
            return new LoadResult(new Settings(true, credential, capacity, transports),
                    Optional.empty());
        } catch (IllegalArgumentException error) {
            return invalid(error.getMessage());
        }
    }

    private static Set<MediaTransport> parseTransports(String value) {
        EnumSet<MediaTransport> result = EnumSet.noneOf(MediaTransport.class);
        for (String item : value.split(",")) {
            String normalized = item.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                result.add(MediaTransport.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(
                        "unknown agent transport: " + normalized, error);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("agent.transports must not be empty");
        }
        return Set.copyOf(result);
    }

    private static LoadResult invalid(String message) {
        return new LoadResult(Settings.disabled(), Optional.ofNullable(message)
                .or(() -> Optional.of("invalid render-agent configuration")));
    }
}
