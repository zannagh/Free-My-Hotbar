package io.github.zannagh.freemyhotbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeMyHotbar {

    public static final String MOD_ID = "free-my-hotbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Initializing {}", MOD_ID);
    }
}
