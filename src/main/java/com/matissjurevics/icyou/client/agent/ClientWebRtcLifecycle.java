package com.matissjurevics.icyou.client.agent;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;
import com.matissjurevics.icyou.render.webrtc.WebRtcAnswerC2SPayload;
import com.matissjurevics.icyou.render.webrtc.WebRtcCloseS2CPayload;
import com.matissjurevics.icyou.render.webrtc.WebRtcOfferS2CPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Loads native WebRTC only for agents that explicitly advertise it. */
public final class ClientWebRtcLifecycle {

    private static WebRtcAgent agent;

    private ClientWebRtcLifecycle() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> restart());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (agent != null) agent.tick();
        });
        ClientPlayNetworking.registerGlobalReceiver(WebRtcOfferS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (agent != null) agent.offer(context.client(), payload.offer());
                }));
        ClientPlayNetworking.registerGlobalReceiver(WebRtcCloseS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (agent != null) agent.close(payload.close());
                }));
    }

    private static void restart() {
        close();
        if (!ClientRenderAgentLifecycle.supports(MediaTransport.WEBRTC)) return;
        try {
            agent = new WebRtcAgent(answer -> ClientPlayNetworking.send(
                    new WebRtcAnswerC2SPayload(answer)));
        } catch (LinkageError | RuntimeException error) {
            ICyouMod.LOGGER.error("WebRTC native runtime could not start", error);
        }
    }

    private static void close() {
        if (agent != null) {
            agent.close();
            agent = null;
        }
    }
}
