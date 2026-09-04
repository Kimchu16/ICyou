package com.matissjurevics.icyou.client.agent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.client.agent.SceneSnapshotAssembler.CompleteSnapshot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

/** Creates, updates, ticks, and closes isolated worlds for active render jobs. */
public final class ClientRemoteSceneLifecycle {

    private static final RemoteSceneWorldRegistry<RemoteSceneWorld> WORLDS =
            new RemoteSceneWorldRegistry<>();

    private ClientRemoteSceneLifecycle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientRemoteSceneLifecycle::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> closeAll());
    }

    public static Map<UUID, RemoteSceneWorld> worlds() {
        return WORLDS.resources();
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.getNetworkHandler() == null) {
            closeAll();
            return;
        }
        ClientRenderJobExecutor executor = ClientRenderAgentLifecycle.executor();
        Set<UUID> activeJobs = executor.jobs().keySet();
        WORLDS.retain(executor.snapshots().keySet());

        for (CompleteSnapshot snapshot : executor.snapshots().values()) {
            UUID jobId = snapshot.begin().jobId();
            if (!activeJobs.contains(jobId)) {
                continue;
            }
            try {
                RemoteSceneWorld current = WORLDS.install(snapshot,
                        value -> new RemoteSceneWorld(client, value));
                for (var delta : executor.drainDeltas(jobId)) {
                    current.apply(delta);
                }
                current.tick();
            } catch (RuntimeException error) {
                ICyouMod.LOGGER.error("Remote camera world failed for job {}", jobId, error);
                close(jobId);
                ClientRenderAgentLifecycle.agent().markFailed(jobId,
                        "remote camera world failed");
            }
        }
    }

    private static void close(UUID jobId) {
        WORLDS.remove(jobId);
    }

    private static void closeAll() {
        WORLDS.clear();
    }
}
