package com.matissjurevics.icyou.client.stream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Stream server config, read from {@code config/icyou-stream.properties}.
 * Off by default and bound to loopback so the mod never opens a port unless
 * the user opts in.
 */
public final class StreamConfig {

    private StreamConfig() {}

    public static boolean enabled = false;
    public static String bind = "127.0.0.1";
    public static int port = 8123;

    public static void load() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("icyou-stream.properties");
            if (Files.exists(p)) {
                Properties props = new Properties();
                try (var in = Files.newInputStream(p)) {
                    props.load(in);
                }
                enabled = Boolean.parseBoolean(props.getProperty("stream.enabled", "false"));
                bind = props.getProperty("stream.bind", "127.0.0.1");
                port = Integer.parseInt(props.getProperty("stream.port", "8123"));
            }
        } catch (Exception e) {
            enabled = false;
            bind = "127.0.0.1";
            port = 8123;
        }
    }
}
