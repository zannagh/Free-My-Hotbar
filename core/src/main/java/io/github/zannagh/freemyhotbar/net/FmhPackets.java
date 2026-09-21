package io.github.zannagh.freemyhotbar.net;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.slot.SlotLockState;

/**
 * Free My Hotbar's eunomia packet types and their server-side handler.
 *
 * <p>The channel key {@code free-my-hotbar:locked_slots} is part of the wire contract in two ways:
 * it is what a client sends on and what a server advertises in its handshake, and the client reads
 * that advertisement back to decide whether the server enforces locks (see
 * {@code ServerPresenceResolver}). Changing it silently breaks both directions against every
 * already-released build, so it is pinned by a test in {@code :smoke}.
 */
public final class FmhPackets {

    /** Client-to-server: the sender's hotbar slots and their blocked state. */
    public static final PacketType<LockedSlotsPayload> LOCKED_SLOTS =
            PacketType.serverbound(FreeMyHotbar.MOD_ID, "locked_slots", LockedSlotsPayload.class);

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
        CommunicationManager.onServerReceive(
                LOCKED_SLOTS,
                (payload, ctx) -> SlotLockState.set(ctx.senderId(), payload.slots()));
    }
}
