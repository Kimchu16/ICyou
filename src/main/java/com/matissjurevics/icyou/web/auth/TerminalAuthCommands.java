package com.matissjurevics.icyou.web.auth;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.web.auth.TerminalCredentialStore.Scope;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Owner/operator commands for issuing and revoking web credentials. */
public final class TerminalAuthCommands {

    private TerminalAuthCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("icyou")
                        .then(literal("token")
                                .then(literal("issue")
                                        .then(argument("terminal", StringArgumentType.word())
                                                .then(argument("scope", StringArgumentType.word())
                                                        .suggests((context, builder) -> builder
                                                                .suggest("viewer").suggest("owner")
                                                                .buildFuture())
                                                        .executes(TerminalAuthCommands::issue))))
                                .then(literal("revoke")
                                        .then(argument("terminal", StringArgumentType.word())
                                                .then(argument("credential", StringArgumentType.word())
                                                        .executes(TerminalAuthCommands::revoke))))
                                .then(literal("revoke-all")
                                        .then(argument("terminal", StringArgumentType.word())
                                                .then(argument("scope", StringArgumentType.word())
                                                        .suggests((context, builder) -> builder
                                                                .suggest("viewer").suggest("owner")
                                                                .buildFuture())
                                                        .executes(TerminalAuthCommands::revokeAll)))))));
    }

    private static int issue(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        TerminalTarget target = target(source,
                StringArgumentType.getString(context, "terminal"));
        Scope scope = scope(source, StringArgumentType.getString(context, "scope"));
        if (target == null || scope == null) {
            return 0;
        }
        var issued = TerminalCredentialStore.get(source.getServer()).issue(
                target.terminalId(), scope);
        source.sendFeedback(() -> Text.literal("ICyou " + scope.name().toLowerCase(Locale.ROOT)
                + " token (shown once): " + issued.token()), false);
        source.sendFeedback(() -> Text.literal(
                "Credential ID for revocation: " + issued.credentialId()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int revoke(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        TerminalTarget target = target(source,
                StringArgumentType.getString(context, "terminal"));
        if (target == null) {
            return 0;
        }
        UUID credentialId;
        try {
            credentialId = UUID.fromString(
                    StringArgumentType.getString(context, "credential"));
        } catch (IllegalArgumentException error) {
            source.sendError(Text.literal("Invalid credential ID."));
            return 0;
        }
        boolean removed = TerminalCredentialStore.get(source.getServer())
                .revoke(target.terminalId(), credentialId);
        source.sendFeedback(() -> Text.literal(removed
                ? "Credential revoked." : "Credential was not found."), false);
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int revokeAll(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        TerminalTarget target = target(source,
                StringArgumentType.getString(context, "terminal"));
        Scope scope = scope(source, StringArgumentType.getString(context, "scope"));
        if (target == null || scope == null) {
            return 0;
        }
        int removed = TerminalCredentialStore.get(source.getServer())
                .revokeAll(target.terminalId(), scope);
        source.sendFeedback(() -> Text.literal("Revoked " + removed + " "
                + scope.name().toLowerCase(Locale.ROOT) + " credential(s)."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static TerminalTarget target(ServerCommandSource source, String slug) {
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(source.getServer());
        var terminal = registry.terminalBySlug(slug);
        if (terminal.isEmpty()) {
            source.sendError(Text.literal("Terminal not found."));
            return null;
        }
        UUID terminalId = terminal.get().ref().deviceId();
        ServerPlayerEntity player = source.getEntity() instanceof ServerPlayerEntity serverPlayer
                ? serverPlayer : null;
        boolean allowed = source.hasPermissionLevel(2) || player != null
                && registry.canManageTerminal(terminalId, player.getUuid(), false);
        if (!allowed) {
            source.sendError(Text.literal("Only the terminal owner or an operator can manage tokens."));
            return null;
        }
        return new TerminalTarget(terminalId);
    }

    private static Scope scope(ServerCommandSource source, String value) {
        try {
            return Scope.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            source.sendError(Text.literal("Scope must be viewer or owner."));
            return null;
        }
    }

    private record TerminalTarget(UUID terminalId) {
    }
}
