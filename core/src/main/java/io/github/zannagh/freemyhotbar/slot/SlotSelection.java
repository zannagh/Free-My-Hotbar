package io.github.zannagh.freemyhotbar.slot;

import java.util.Set;
import java.util.function.IntPredicate;

public final class SlotSelection {

    private SlotSelection() {
    }

    /**
     * Returns true when a blocked entry for {@code slotId} is present in {@code blockedSlots}.
     * Negative ids are never blocked (and never constructed, since {@link SlotBlock} rejects them).
     */
    public static boolean isBlocked(Set<SlotBlock> blockedSlots, int slotId) {
        return slotId >= 0 && blockedSlots != null && blockedSlots.contains(new SlotBlock(slotId, true));
    }

    /**
     * Returns the first index in {@code [0, slotCount)} that is not blocked and passes
     * {@code usable}, or -1 when no such index exists.
     */
    public static int firstAllowed(Set<SlotBlock> blockedSlots, int slotCount, IntPredicate usable) {
        for (int i = 0; i < slotCount; i++) {
            if (!isBlocked(blockedSlots, i) && usable.test(i)) {
                return i;
            }
        }
        return -1;
    }
}
