package io.github.zannagh.freemyhotbar.client;

import java.util.function.IntConsumer;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.config.ClientSlotConfig;

/**
 * Common (loader-agnostic) client init hook. Loads the client config singleton and exposes it, plus
 * a seam for a loader to install the mask sync sender.
 */
public final class FreeMyHotbarClient {

    private static ClientSlotConfig config;

    private FreeMyHotbarClient() {
    }

    public static void init() {
        FreeMyHotbar.LOGGER.info("Initializing {} client", FreeMyHotbar.MOD_ID);
        initConfig();
    }

    /** Loads the client config singleton if it is not already loaded. */
    public static synchronized void initConfig() {
        if (config == null) {
            config = ClientSlotConfig.load();
        }
    }

    /**
     * Returns the client config singleton, loading it on demand.
     *
     * @return the shared config instance, never null.
     */
    public static synchronized ClientSlotConfig config() {
        if (config == null) {
            config = ClientSlotConfig.load();
        }
        return config;
    }

    /**
     * Wires the config's sync callback so mask changes reach the loader's packet sender.
     *
     * @param sender consumer of the mask; a loader supplies its networking send here.
     */
    public static void setSyncSender(IntConsumer sender) {
        config().setSyncCallback(sender);
    }
}
