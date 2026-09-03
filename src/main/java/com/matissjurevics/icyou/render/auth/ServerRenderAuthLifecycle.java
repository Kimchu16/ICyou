package com.matissjurevics.icyou.render.auth;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.demand.ServerDemandLifecycle;
import com.matissjurevics.icyou.render.protocol.RenderControlC2SPayload;
import com.matissjurevics.icyou.render.protocol.RenderControlS2CPayload;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AgentHello;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.AuthProof;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.JobStatus;
import com.matissjurevics.icyou.render.schedule.ServerRenderSchedulerLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

/** Binds render authentication and sessions to one logical server. */
public final class ServerRenderAuthLifecycle {

    private static final Map<MinecraftServer, RenderAgentAuthenticator> ACTIVE =
            new IdentityHashMap<>();

    private ServerRenderAuthLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerRenderAuthLifecycle::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerRenderAuthLifecycle::stop);
        ServerTickEvents.END_SERVER_TICK.register(ServerRenderAuthLifecycle::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                authenticator(server).ifPresent(auth ->
                        auth.disconnect(handler.getPlayer().getUuid())));
        ServerPlayNetworking.registerGlobalReceiver(RenderControlC2SPayload.ID,
                (payload, context) -> handle(payload, context.player()));
    }

    public static synchronized Optional<RenderAgentAuthenticator> authenticator(
            MinecraftServer server) {
        return Optional.ofNullable(ACTIVE.get(server));
    }

    public static Optional<RenderAgentCredentialStore.IssuedCredential> issue(
            MinecraftServer server, UUID minecraftId) {
        return authenticator(server).map(ignored ->
                RenderAgentCredentialStore.get(server).issue(minecraftId));
    }

    public static boolean revokeCredential(MinecraftServer server, UUID credentialId) {
        boolean removed = RenderAgentCredentialStore.get(server).revoke(credentialId);
        if (removed) {
            authenticator(server).ifPresent(auth -> disconnect(server,
                    auth.revokeCredential(credentialId), "Render-agent credential revoked."));
        }
        return removed;
    }

    public static int revokeAll(MinecraftServer server, UUID minecraftId) {
        RenderAgentCredentialStore store = RenderAgentCredentialStore.get(server);
        var credentialIds = store.credentialIds(minecraftId);
        int removed = store.revokeAll(minecraftId);
        authenticator(server).ifPresent(auth -> {
            java.util.LinkedHashSet<UUID> players = new java.util.LinkedHashSet<>();
            credentialIds.forEach(id -> players.addAll(auth.revokeCredential(id)));
            disconnect(server, players, "Render-agent access revoked.");
        });
        return removed;
    }

    private static synchronized void start(MinecraftServer server) {
        RenderAgentAuthenticator auth = new RenderAgentAuthenticator(
                RenderAgentCredentialStore.get(server));
        ACTIVE.put(server, auth);
        ServerDemandLifecycle.setRenderAgentPredicate(server, auth::isAuthenticated);
    }

    private static synchronized void stop(MinecraftServer server) {
        RenderAgentAuthenticator auth = ACTIVE.remove(server);
        if (auth != null) {
            auth.clear();
        }
    }

    private static void tick(MinecraftServer server) {
        authenticator(server).ifPresent(auth -> {
            auth.expire(Instant.now());
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (auth.isAuthenticated(player.getUuid()) && !player.isSpectator()) {
                    player.changeGameMode(GameMode.SPECTATOR);
                }
            }
        });
    }

    private static void handle(RenderControlC2SPayload payload, ServerPlayerEntity player) {
        authenticator(player.getServer()).ifPresent(auth -> {
            switch (payload.message()) {
                case AgentHello hello -> ServerPlayNetworking.send(player,
                        new RenderControlS2CPayload(auth.begin(
                                player.getUuid(), hello, Instant.now())));
                case AuthProof proof -> {
                    var completion = auth.complete(player.getUuid(), proof, Instant.now());
                    if (completion.session().isPresent()) {
                        player.changeGameMode(GameMode.SPECTATOR);
                    }
                    ServerPlayNetworking.send(player,
                            new RenderControlS2CPayload(completion.response()));
                }
                case JobStatus status -> ServerRenderSchedulerLifecycle.handleStatus(
                        player.getServer(), player.getUuid(), status);
            }
        });
    }

    private static void disconnect(MinecraftServer server, Set<UUID> playerIds,
                                   String reason) {
        for (UUID playerId : playerIds) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.networkHandler.disconnect(Text.literal(reason));
            }
        }
    }
}
