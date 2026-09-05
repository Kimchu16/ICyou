package com.matissjurevics.icyou.client.agent;

import com.matissjurevics.icyou.client.agent.RemoteAudioSceneStore.InstallResult;
import com.matissjurevics.icyou.render.audio.AudioSceneS2CPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Verifies and retains remote sounds without playing them in the agent world. */
public final class ClientAudioSceneLifecycle {

    private static final RemoteAudioSceneStore AUDIO = new RemoteAudioSceneStore();

    private ClientAudioSceneLifecycle() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AudioSceneS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> accept(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> AUDIO.retain(
                ClientRenderAgentLifecycle.agent().activeJobs().keySet()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AUDIO.clear());
    }

    public static RemoteAudioSceneStore store() {
        return AUDIO;
    }

    private static void accept(AudioSceneS2CPayload payload) {
        var batch = payload.batch();
        var job = ClientRenderAgentLifecycle.agent().activeJobs().get(batch.jobId());
        var snapshot = ClientRenderAgentLifecycle.executor().snapshots().get(batch.jobId());
        if (job == null || snapshot == null || job.revision() != batch.jobRevision()
                || snapshot.begin().sequence() != batch.snapshotSequence()) {
            return;
        }
        InstallResult result = AUDIO.install(batch);
        if (result == InstallResult.GAP || result == InstallResult.MISMATCH) {
            ClientRenderAgentLifecycle.agent().markFailed(batch.jobId(),
                    "remote audio scene sequence failed");
        }
    }
}
