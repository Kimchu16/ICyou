package com.matissjurevics.icyou.admin;

import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;

/** Shows the immutable resource limits loaded for this server run. */
public final class AdminLimitsCommands {

    private AdminLimitsCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("icyou")
                        .then(literal("limits")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    var source = context.getSource();
                                    var limits = ServerAdminLimitsLifecycle.limits(
                                            source.getServer());
                                    source.sendFeedback(() -> Text.literal(
                                            "ICyou limits: " + limits.registeredCameras()
                                                    + " registered cameras, "
                                                    + limits.activeCameras()
                                                    + " active cameras."), false);
                                    source.sendFeedback(() -> Text.literal(
                                            "Viewers: " + limits.viewersPerCamera()
                                                    + " per camera, "
                                                    + limits.totalViewers()
                                                    + " total. Scene: "
                                                    + limits.simulatedChunkDiameter() + "x"
                                                    + limits.simulatedChunkDiameter()
                                                    + " chunks, "
                                                    + limits.resourceGraceSeconds()
                                                    + "s grace."), false);
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }
}
