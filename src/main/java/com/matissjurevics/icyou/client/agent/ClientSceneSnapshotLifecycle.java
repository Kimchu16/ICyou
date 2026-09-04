package com.matissjurevics.icyou.client.agent;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotBegin;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.SnapshotPart;
import com.matissjurevics.icyou.render.scene.SceneSnapshotS2CPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Verifies and installs complete scene snapshots for accepted local jobs. */
public final class ClientSceneSnapshotLifecycle {

    private static final SceneSnapshotAssembler ASSEMBLER = new SceneSnapshotAssembler(
            CameraOverhaulContracts.MAX_ACTIVE_CAMERAS);

    private ClientSceneSnapshotLifecycle() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SceneSnapshotS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> handle(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> ASSEMBLER.retainJobs(
                ClientRenderAgentLifecycle.agent().activeJobs().keySet()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ASSEMBLER.clear());
    }

    private static void handle(SceneSnapshotS2CPayload payload) {
        switch (payload.message()) {
            case SnapshotBegin begin -> {
                var job = ClientRenderAgentLifecycle.agent().activeJobs().get(begin.jobId());
                if (job != null && job.revision() == begin.jobRevision()
                        && job.camera().equals(begin.camera())) {
                    ASSEMBLER.begin(begin);
                }
            }
            case SnapshotPart part -> ASSEMBLER.part(part).complete().ifPresent(snapshot ->
                    ClientRenderAgentLifecycle.executor().installSnapshot(snapshot));
        }
    }
}
