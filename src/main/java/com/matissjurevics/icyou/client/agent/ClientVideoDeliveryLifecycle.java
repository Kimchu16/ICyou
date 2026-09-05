package com.matissjurevics.icyou.client.agent;

import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;
import com.matissjurevics.icyou.render.video.VideoFrameC2SPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Owns bounded background JPEG publication for MJPEG-capable agents. */
public final class ClientVideoDeliveryLifecycle {

    private static ClientVideoPublisher publisher;

    private ClientVideoDeliveryLifecycle() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> restart());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (publisher != null) {
                publisher.tick(client);
            }
        });
    }

    private static void restart() {
        close();
        if (ClientRenderAgentLifecycle.supports(MediaTransport.MJPEG)) {
            publisher = new ClientVideoPublisher(JpegFrameEncoder::encode,
                    frame -> ClientPlayNetworking.send(new VideoFrameC2SPayload(frame)));
        }
    }

    private static void close() {
        if (publisher != null) {
            publisher.close();
            publisher = null;
        }
    }
}
