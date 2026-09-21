package io.github.zannagh.freemyhotbar.net;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.packets.ServerContext;
import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.slot.SlotLockState;

/**
 * Free My Hotbar's eunomia packet types and their server-side handler.
 *
 * <p>The channel key {@code free-my-hotbar:locked_slots_v2} is part of the wire contract in two
 * ways: it is what a client sends on and what a server advertises in its handshake, and the client
 * reads that advertisement back to decide whether the server enforces locks (see
 * {@code ServerPresenceResolver}). Changing it silently breaks both directions against every
 * already-released build, so it is pinned by a test in {@code :smoke}.
 *
 * <p><b>Why {@code _v2}.</b> The payload format changed from a hand-rolled VarInt encoding to
 * eunomia's gzip(JSON) {@code PayloadCodec}. Keeping the old path would route a pre-eunomia
 * client's binary payload straight into the JSON decoder, which fails in a way that reads like a
 * corrupt connection rather than a version mismatch. Versioning the path instead means an old
 * client simply finds no handler for its channel, resolves the server as ABSENT and falls back to
 * client-side eviction. The wire break is deliberate: there is no legacy decoder.
 */
public final class FmhPackets {

    /** Client-to-server: the sender's hotbar slots and their blocked state. */
    public static final PacketType<LockedSlotsPayload> LOCKED_SLOTS =
            PacketType.serverbound(FreeMyHotbar.MOD_ID, "locked_slots_v2", LockedSlotsPayload.class);

    private static boolean registered;

    private FmhPackets() {
    }

    /**
     * Registers the server-side handler for {@link #LOCKED_SLOTS}.
     *
     * <p>Must be called EAGERLY at mod init from BOTH physical sides, never lazily when a player
     * joins: a server answers a client's capability probe with the channels it has handlers for at
     * that moment, so a handler registered later is invisible to clients that connected earlier.
     * On a physical client the same registration serves the integrated singleplayer server.
     *
     * <p>Idempotent, so a loader may call it from more than one entrypoint.
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CommunicationManager.onServerReceive(LOCKED_SLOTS, FmhPackets::handle);
    }

    /**
     * Stores a sender's locks, after bounding the payload's cardinality.
     *
     * <p>The bound is the security-relevant half. The hand-rolled decoder this replaced refused a
     * count above 64 while reading the wire; the JSON codec has no such notion, so a hostile client
     * could otherwise hand the server an arbitrarily long slot list (eunomia permits ~32 KiB
     * gzipped and 64 MiB inflated serverbound) and have it walked entry by entry, repeatedly. A
     * legitimate payload can never exceed one entry per hotbar slot, so anything longer is dropped
     * whole rather than sanitized — it is not a payload this mod ever produces.
     */
    private static void handle(LockedSlotsPayload payload,
            ServerContext ctx) {
        if (payload == null || !payload.withinBounds()) {
            FreeMyHotbar.LOGGER.warn(
                    "Dropping oversized locked-slots payload from {} ({} entries, max {})",
                    ctx != null ? ctx.senderName() : "<unknown>",
                    payload != null ? payload.slots().size() : -1,
                    LockedSlotsPayload.MAX_SLOTS);
            return;
        }
        SlotLockState.set(ctx.senderId(), payload.slots());
    }
}
