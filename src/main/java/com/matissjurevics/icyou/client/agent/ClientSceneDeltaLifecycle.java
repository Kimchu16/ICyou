package com.matissjurevics.icyou.client.agent;

import com.matissjurevics.icyou.client.agent.ClientRenderJobExecutor.DeltaResult;
import com.matissjurevics.icyou.render.scene.SceneDeltaS2CPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Installs only consecutive deltas and fails a job on a sequence gap. */
public final class ClientSceneDeltaLifecycle {

    private ClientSceneDeltaLifecycle() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SceneDeltaS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    DeltaResult result = ClientRenderAgentLifecycle.executor()
                            .installDelta(payload.delta());
                    if (result == DeltaResult.GAP) {
                        ClientRenderAgentLifecycle.agent().markFailed(
                                payload.delta().jobId(), "scene update gap; resnapshot required");
                    }
                }));
    }
}
