package io.github.zannagh.freemyhotbar.slot;

import java.util.function.IntPredicate;

public final class SlotSelection {

    private SlotSelection() {
    }

    /**
     * Returns true when {@code slot} is a hotbar slot (0-8) whose lock bit is set in {@code mask}.
     */
    public static boolean isLocked(int mask, int slot) {
        if (slot < 0 || slot >= 9) {
            return false;
        }
        return (mask & (1 << slot)) != 0;
    }

    /**
     * Returns the first index in {@code [0, slotCount)} that is not locked and passes
     * {@code usable}, or -1 when no such index exists.
     */
    public static int firstAllowed(int mask, int slotCount, IntPredicate usable) {
        for (int i = 0; i < slotCount; i++) {
            if (!isLocked(mask, i) && usable.test(i)) {
                return i;
            }
        }
        return -1;
    }
}
