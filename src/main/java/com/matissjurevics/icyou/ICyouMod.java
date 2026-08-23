package com.matissjurevics.icyou;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ICyouMod implements ModInitializer {

    public static final String MOD_ID = "icyou";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("ICyou has been initialized!");
    }
}
