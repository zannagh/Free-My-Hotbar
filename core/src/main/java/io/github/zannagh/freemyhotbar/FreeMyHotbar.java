package io.github.zannagh.freemyhotbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeMyHotbar {

    public static final String MOD_ID = "free-my-hotbar";

    /**
     * The mod id in its underscored form. Forge's {@code @Mod} annotation value and the Forge mod
     * id in {@code mods.toml} must match this exactly; the dashed {@link #MOD_ID} is the resource
     * namespace used everywhere else (Fabric mod id, channel namespace, translation keys).
     */
    public static final String FORGE_MOD_ID = "free_my_hotbar";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Initializing {}", MOD_ID);
    }
}
