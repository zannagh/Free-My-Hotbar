package io.github.zannagh.freemyhotbar.client;

import java.util.List;
import java.util.function.Consumer;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.config.ClientSlotConfig;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;

/**
 * Common (loader-agnostic) client init hook. Loads the client config singleton and exposes it, plus
 * a seam for a loader to install the slot sync sender.
 */
public final class FreeMyHotbarClient {

    private static ClientSlotConfig config;

    private FreeMyHotbarClient() {
    }

    public static void init() {
        FreeMyHotbar.LOGGER.info("Initializing {} client", FreeMyHotbar.MOD_ID);
        initConfig();
    }

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
     * Wires the config's sync callback so slot changes reach the loader's packet sender.
     *
     * @param sender consumer of the slot list; a loader supplies its networking send here.
     */
    public static void setSyncSender(Consumer<List<SlotBlock>> sender) {
        config().setSyncCallback(sender);
    }
}
