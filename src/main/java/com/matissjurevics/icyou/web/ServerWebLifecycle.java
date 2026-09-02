package com.matissjurevics.icyou.web;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

import com.matissjurevics.icyou.ICyouMod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/** Binds web listener lifetime to one logical Minecraft server. */
public final class ServerWebLifecycle {

    private static final Map<MinecraftServer, ServerWebRuntime> RUNTIMES =
            new IdentityHashMap<>();

    private ServerWebLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerWebLifecycle::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerWebLifecycle::stop);
    }

    static synchronized void start(MinecraftServer server) {
        if (RUNTIMES.containsKey(server)) {
            return;
        }
        Path configFile = FabricLoader.getInstance().getConfigDir()
                .resolve("icyou-web.properties");
        WebServerConfig config;
        try {
            config = WebServerConfig.load(configFile);
        } catch (Exception error) {
            ICyouMod.LOGGER.error("Invalid ICyou web configuration; listener disabled", error);
            return;
        }
        if (!config.enabled()) {
            ICyouMod.LOGGER.info("ICyou server web listener is disabled");
            return;
        }
        ServerWebRuntime runtime = new ServerWebRuntime(error ->
                ICyouMod.LOGGER.error("ICyou server web listener failed", error));
        if (runtime.start(config)) {
            RUNTIMES.put(server, runtime);
            var address = runtime.boundAddress().orElseThrow();
            ICyouMod.LOGGER.info("ICyou server web listener started on {}:{}",
                    address.getHostString(), address.getPort());
        }
    }

    static synchronized void stop(MinecraftServer server) {
        ServerWebRuntime runtime = RUNTIMES.remove(server);
        if (runtime != null) {
            runtime.close();
            ICyouMod.LOGGER.info("ICyou server web listener stopped");
        }
    }

    static synchronized int activeServerCount() {
        return RUNTIMES.size();
    }
}
