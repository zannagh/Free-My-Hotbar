package io.github.zannagh.freemyhotbar.slot;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side, per-player store of the 9-bit hotbar lock mask (bits 0-8, one per hotbar slot).
 * A set bit marks a slot as locked so items are not auto-picked-up into it. Backed by a
 * {@link ConcurrentHashMap} keyed on the player UUID so it is safe to read and write from the
 * server thread and networking callbacks. This class has no client or loader dependencies.
 */
public final class SlotLockState {

    private static final int MASK_BITS = 0x1FF;

    private static final ConcurrentHashMap<UUID, Integer> masks = new ConcurrentHashMap<>();

    private SlotLockState() {
    }

    /**
     * Stores the lock mask for a player, clamping it to the low 9 bits.
     */
    public static void set(UUID player, int mask) {
        if (player == null) {
            return;
        }
        masks.put(player, mask & MASK_BITS);
    }

    /**
     * Returns the stored lock mask for a player UUID, or 0 when none is set.
     */
    public static int mask(UUID player) {
        if (player == null) {
            return 0;
        }
        return masks.getOrDefault(player, 0);
    }

    /**
     * Drops the stored mask for a player (e.g. on logout).
     */
    public static void remove(UUID player) {
        if (player == null) {
            return;
        }
        masks.remove(player);
    }

    /**
     * Clears every stored mask.
     */
    public static void clear() {
        masks.clear();
    }
}
