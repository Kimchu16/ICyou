package com.matissjurevics.icyou.admin;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/** Loads one immutable limit set for each logical server lifetime. */
public final class ServerAdminLimitsLifecycle {

    private static final Map<MinecraftServer, CameraAdminLimits> ACTIVE =
            new IdentityHashMap<>();

    private ServerAdminLimitsLifecycle() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerAdminLimitsLifecycle::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerAdminLimitsLifecycle.class) {
                ACTIVE.remove(server);
            }
        });
    }

    private static void start(MinecraftServer server) {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve(CameraAdminLimits.FILE_NAME);
        CameraAdminLimits limits;
        try {
            limits = CameraAdminLimits.load(file);
        } catch (Exception error) {
            ICyouMod.LOGGER.error("Invalid ICyou camera limits; using safe defaults", error);
            limits = CameraAdminLimits.DEFAULTS;
        }
        synchronized (ServerAdminLimitsLifecycle.class) {
            ACTIVE.put(server, limits);
        }
        GlobalDeviceRegistry.get(server).setRegisteredCameraLimit(
                limits.registeredCameras());
        ICyouMod.LOGGER.info("ICyou camera limits loaded: {} registered, {} active, "
                        + "{}/{} viewers, {}x{} chunks, {}s grace",
                limits.registeredCameras(), limits.activeCameras(),
                limits.viewersPerCamera(), limits.totalViewers(),
                limits.simulatedChunkDiameter(), limits.simulatedChunkDiameter(),
                limits.resourceGraceSeconds());
    }

    public static synchronized CameraAdminLimits limits(MinecraftServer server) {
        return ACTIVE.getOrDefault(server, CameraAdminLimits.DEFAULTS);
    }
}
