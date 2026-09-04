package com.matissjurevics.icyou.observability;

import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;

/** Shows a privacy-safe live camera summary to server operators. */
public final class ObservabilityCommands {

    private ObservabilityCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("icyou")
                        .then(literal("status")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    ServerCameraObservability.snapshot(
                                            context.getSource().getServer()).lines()
                                            .forEach(line -> context.getSource().sendFeedback(
                                                    () -> Text.literal(line), false));
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }
}
