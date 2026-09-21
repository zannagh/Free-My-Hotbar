package io.github.zannagh.freemyhotbar.client;

import java.util.List;

import de.zannagh.eunomia.client.networking.ClientConnectionEvents;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.comms.SendOptions;
import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.config.ClientSlotConfig;
import io.github.zannagh.freemyhotbar.client.gui.SlotLockEntryPoint;
import io.github.zannagh.freemyhotbar.net.FmhPackets;
import io.github.zannagh.freemyhotbar.net.LockedSlotsPayload;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.minecraft.client.Minecraft;

/**
 * Common (loader-agnostic) client init hook. Loads the client config singleton, wires its sync
 * callback to the slot-update packet, and owns the per-connection lifecycle: eunomia's
 * {@code ClientConnectionEvents} fire on both loaders, so join and disconnect handling lives here
 * rather than being duplicated in each loader's glue. It also installs the options-screen entry
 * point for the lock screen (see {@link SlotLockEntryPoint}).
 */
public final class FreeMyHotbarClient {

    private static ClientSlotConfig config;

    private static boolean connectionEventsRegistered;

    private FreeMyHotbarClient() {
    }

    /** Loads the config and wires the sync callback and connection events. Idempotent. */
    public static void init() {
        FreeMyHotbar.LOGGER.info("Initializing {} client", FreeMyHotbar.MOD_ID);
        initConfig();
        config().setSyncCallback(FreeMyHotbarClient::sendSlots);
        registerConnectionEvents();
        // Takes over the options-screen entry eunomia would otherwise install for itself, so the
        // button there opens the lock screen. The H keybind keeps working alongside it.
        SlotLockEntryPoint.install();
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
     * Runs the client-side per-tick work. Called once per client tick from each loader's glue.
     *
     * @param minecraft the client instance; null is ignored by every step.
     */
    public static void clientTick(Minecraft minecraft) {
        ServerPresenceTracker.tick(minecraft);
        FreeMyHotbarKeys.handleTick(minecraft);
        ServerModNotice.tick(minecraft);
        HotbarEvictor.tick(minecraft);
    }

    /**
     * Sends the given slots to the server.
     *
     * <p>{@link SendOptions#IF_SERVER_SUPPORTS} rather than the default: the packet is meaningless
     * to a server that runs eunomia but not this mod, and the option also queues the send until the
     * capability probe resolves, so a send fired before the handshake completes is not lost.
     *
     * @param slots the slots with their blocked state.
     */
    private static void sendSlots(List<SlotBlock> slots) {
        CommunicationManager.sendToServer(
                FmhPackets.LOCKED_SLOTS,
                new LockedSlotsPayload(slots),
                SendOptions.IF_SERVER_SUPPORTS);
    }

    private static synchronized void registerConnectionEvents() {
        if (connectionEventsRegistered) {
            return;
        }
        connectionEventsRegistered = true;
        ClientConnectionEvents.registerJoin((handler, client) -> onJoin());
        ClientConnectionEvents.registerDisconnect(client -> onDisconnect());
    }

    private static void onJoin() {
        ServerModNotice.reset();
        // A modded server learns the locks from this send; presence itself is derived per tick
        // from the capability handshake (see ServerPresenceTracker).
        sendSlots(config().slots());
    }

    private static void onDisconnect() {
        ServerModPresence.reset();
        ServerModNotice.reset();
        // The fallback's per-connection drop budget must not carry over to the next server.
        HotbarEvictor.reset();
    }
}
