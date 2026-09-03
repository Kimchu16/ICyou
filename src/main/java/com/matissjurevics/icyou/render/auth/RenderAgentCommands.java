package com.matissjurevics.icyou.render.auth;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Operator-only commands for issuing and revoking render-agent credentials. */
public final class RenderAgentCommands {

    private RenderAgentCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("icyou")
                        .then(literal("render-agent")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(literal("issue")
                                        .then(argument("player", StringArgumentType.word())
                                                .executes(RenderAgentCommands::issue)))
                                .then(literal("revoke")
                                        .then(argument("credential", StringArgumentType.word())
                                                .executes(RenderAgentCommands::revoke)))
                                .then(literal("revoke-all")
                                        .then(argument("player", StringArgumentType.word())
                                                .executes(RenderAgentCommands::revokeAll))))));
    }

    private static int issue(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        UUID playerId = uuid(source, StringArgumentType.getString(context, "player"),
                "player UUID");
        if (playerId == null) {
            return 0;
        }
        var issued = ServerRenderAuthLifecycle.issue(source.getServer(), playerId);
        if (issued.isEmpty()) {
            source.sendError(Text.literal("Render authentication is not available."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(
                "Render-agent token (shown once): " + issued.orElseThrow().token()), false);
        source.sendFeedback(() -> Text.literal(
                "Credential ID for revocation: " + issued.orElseThrow().credentialId()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int revoke(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        UUID credentialId = uuid(source,
                StringArgumentType.getString(context, "credential"), "credential ID");
        if (credentialId == null) {
            return 0;
        }
        boolean removed = ServerRenderAuthLifecycle.revokeCredential(
                source.getServer(), credentialId);
        source.sendFeedback(() -> Text.literal(removed
                ? "Render-agent credential revoked."
                : "Render-agent credential was not found."), false);
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int revokeAll(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        UUID playerId = uuid(source, StringArgumentType.getString(context, "player"),
                "player UUID");
        if (playerId == null) {
            return 0;
        }
        int removed = ServerRenderAuthLifecycle.revokeAll(source.getServer(), playerId);
        source.sendFeedback(() -> Text.literal("Revoked " + removed
                + " render-agent credential(s)."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static UUID uuid(ServerCommandSource source, String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            source.sendError(Text.literal("Invalid " + label + '.'));
            return null;
        }
    }
}
