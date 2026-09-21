package io.github.zannagh.freemyhotbar;

import de.zannagh.eunomia.server.ServerConnectionEvents;
import io.github.zannagh.freemyhotbar.net.FmhPackets;
import io.github.zannagh.freemyhotbar.slot.SlotLockState;

/**
 * Loader-agnostic networking setup, called from each loader's main entrypoint — which runs on both
 * physical sides, so the locked-slots handler is registered eagerly on a dedicated server AND on a
 * client (for its integrated server) before any capability probe can arrive.
 *
 * <p>Player cleanup rides on eunomia's {@code ServerConnectionEvents}, which both loaders drive
 * from the same mixin, so there is no loader-specific logout wiring left.
 */
public final class FreeMyHotbarNetworking {

    private static boolean initialized;

    private FreeMyHotbarNetworking() {
    }

    /** Registers the mod's packet handlers and server-side player cleanup. Idempotent. */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        FmhPackets.register();
        ServerConnectionEvents.registerDisconnect(SlotLockState::remove);
    }
}
