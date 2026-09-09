package io.github.zannagh.freemyhotbar.slot;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side, per-player store of blocked slots. A stored {@link SlotBlock} marks a slot so items
 * are not auto-picked-up into it. Backed by a {@link ConcurrentHashMap} keyed on the player UUID so
 * it is safe to read and write from the server thread and networking callbacks. This class has no
 * client or loader dependencies.
 */
public final class SlotLockState {

    private static final ConcurrentHashMap<UUID, Set<SlotBlock>> blocked = new ConcurrentHashMap<>();

    private SlotLockState() {
    }

    /**
     * Stores an immutable copy of the blocked slots for a player, keeping only entries that are
     * actually blocked and whose slot id is in the supported range
     * {@code [0, SlotBlock.HOTBAR_SLOT_COUNT)}.
     */
    public static void set(UUID player, Collection<SlotBlock> slots) {
        if (player == null) {
            return;
        }
        Set<SlotBlock> sanitized = new LinkedHashSet<>();
        if (slots != null) {
            for (SlotBlock slot : slots) {
                if (slot != null && slot.blocked()
                        && slot.slotId() >= 0 && slot.slotId() < SlotBlock.HOTBAR_SLOT_COUNT) {
                    sanitized.add(slot);
                }
            }
        }
        blocked.put(player, Set.copyOf(sanitized));
    }

    /**
     * Returns the stored blocked slots for a player UUID, or an empty set when none is set.
     */
    public static Set<SlotBlock> blocked(UUID player) {
        if (player == null) {
            return Set.of();
        }
        return blocked.getOrDefault(player, Set.of());
    }

    /**
     * Drops the stored blocked slots for a player (e.g. on logout).
     */
    public static void remove(UUID player) {
        if (player == null) {
            return;
        }
        blocked.remove(player);
    }

    /**
     * Clears every stored entry.
     */
    public static void clear() {
        blocked.clear();
    }
}
