package com.matissjurevics.icyou.client.agent;

import java.nio.file.Path;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.render.protocol.RenderControlC2SPayload;
import com.matissjurevics.icyou.render.protocol.RenderControlS2CPayload;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthChallenge;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthResult;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobAssignment;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobCancel;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/** Owns one opt-in render-agent session for the current client connection. */
public final class ClientRenderAgentLifecycle {

    private static RenderAgentClient agent;
    private static ClientRenderJobExecutor executor;
    private static boolean awaitingPlayer;
    private static RenderAgentConfig.Settings settings = RenderAgentConfig.Settings.disabled();

    private ClientRenderAgentLifecycle() {
    }

    public static void register() {
        Path configPath = FabricLoader.getInstance().getConfigDir()
                .resolve(RenderAgentConfig.FILE_NAME);
        RenderAgentConfig.LoadResult loaded = RenderAgentConfig.load(configPath);
        settings = loaded.settings();
        executor = new ClientRenderJobExecutor();
        agent = new RenderAgentClient(loaded.settings(), message ->
                ClientPlayNetworking.send(new RenderControlC2SPayload(message)), executor);
        loaded.error().ifPresent(error -> ICyouMod.LOGGER.warn(
                "Render agent is disabled: {}", error));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            agent.disconnect();
            awaitingPlayer = loaded.settings().enabled();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            awaitingPlayer = false;
            agent.disconnect();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientRenderAgentLifecycle::tick);
        ClientPlayNetworking.registerGlobalReceiver(RenderControlS2CPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        handle(context.client(), payload)));
    }

    public static RenderAgentClient agent() {
        return agent;
    }

    public static ClientRenderJobExecutor executor() {
        return executor;
    }

    public static boolean supports(MediaTransport transport) {
        return settings.transports().contains(transport);
    }

    private static void tick(MinecraftClient client) {
        if (awaitingPlayer && client.player != null && client.world != null) {
            awaitingPlayer = false;
            agent.connect(client.player.getUuid());
        }
    }

    private static void handle(MinecraftClient client, RenderControlS2CPayload payload) {
        switch (payload.message()) {
            case AuthChallenge challenge -> agent.challenge(challenge);
            case AuthResult result -> agent.authentication(result);
            case JobAssignment assignment -> {
                if (client.world != null) {
                    agent.assign(assignment, client.world.getRegistryKey());
                }
            }
            case JobCancel cancel -> agent.cancel(cancel);
        }
    }
}
