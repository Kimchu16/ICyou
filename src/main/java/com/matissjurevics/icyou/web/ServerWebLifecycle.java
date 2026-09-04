package com.matissjurevics.icyou.web;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.web.auth.TerminalWebController;
import com.matissjurevics.icyou.web.auth.TerminalCredentialStore;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.render.video.ServerVideoFrameLifecycle;
import com.matissjurevics.icyou.web.demand.WebViewerDemandRegistry;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/** Binds web listener lifetime to one logical Minecraft server. */
public final class ServerWebLifecycle {

    private static final Map<MinecraftServer, ActiveWeb> ACTIVE =
            new IdentityHashMap<>();

    private ServerWebLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerWebLifecycle::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerWebLifecycle::stop);
    }

    static synchronized void start(MinecraftServer server) {
        if (ACTIVE.containsKey(server)) {
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
        WebGateway gateway = new EmbeddedWebGateway(error ->
                ICyouMod.LOGGER.error("ICyou server web listener failed", error));
        WebViewerDemandRegistry demand = new WebViewerDemandRegistry();
        var video = ServerVideoFrameLifecycle.store(server).orElse(null);
        if (video == null) {
            ICyouMod.LOGGER.error("ICyou video frame store is unavailable; listener disabled");
            return;
        }
        TerminalWebController controller = new TerminalWebController(
                GlobalDeviceRegistry.get(server), TerminalCredentialStore.get(server), demand,
                video);
        if (gateway.start(config, controller::handle)) {
            ACTIVE.put(server, new ActiveWeb(gateway, demand));
            var address = gateway.boundAddress().orElseThrow();
            ICyouMod.LOGGER.info("ICyou server web listener started on {}:{}",
                    address.getHostString(), address.getPort());
        }
    }

    static synchronized void stop(MinecraftServer server) {
        ActiveWeb active = ACTIVE.remove(server);
        if (active != null) {
            active.gateway().close();
            active.demand().clear();
            ICyouMod.LOGGER.info("ICyou server web listener stopped");
        }
    }

    static synchronized int activeServerCount() {
        return ACTIVE.size();
    }

    public static synchronized Optional<WebViewerDemandRegistry> demand(
            MinecraftServer server) {
        ActiveWeb active = ACTIVE.get(server);
        return active == null ? Optional.empty() : Optional.of(active.demand());
    }

    private record ActiveWeb(WebGateway gateway, WebViewerDemandRegistry demand) {
    }

}
