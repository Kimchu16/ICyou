package com.matissjurevics.icyou;

import java.time.Instant;

import com.matissjurevics.icyou.device.LegacyDeviceMigration;
import com.matissjurevics.icyou.feed.FeedManager;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import com.matissjurevics.icyou.registry.ModBlocks;
import com.matissjurevics.icyou.registry.ModDataComponentTypes;
import com.matissjurevics.icyou.registry.ModItemGroups;
import com.matissjurevics.icyou.registry.ModItems;
import com.matissjurevics.icyou.registry.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint. Deliberately thin: all registration lives in the
 * {@code registry} package; game logic lives in feature packages
 * ({@code camera}, {@code feed}, ...).
 */
public class ICyouMod implements ModInitializer {

    public static final String MOD_ID = "icyou";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModBlockEntities.register();
        ModDataComponentTypes.register();
        ModNetworking.register();
        ModItems.register();
        ModItemGroups.register();
        FeedManager.init();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LegacyDeviceMigration.migrateIfNeeded(server);
            int purged = com.matissjurevics.icyou.device.GlobalDeviceRegistry.get(server)
                    .purgeExpiredTombstones(Instant.now());
            if (purged > 0) {
                LOGGER.info("Removed {} expired camera tombstones", purged);
            }
        });

        LOGGER.info("ICyou has been initialized!");
    }
}
