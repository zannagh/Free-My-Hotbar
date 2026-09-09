package io.github.zannagh.freemyhotbar.slot;

import java.util.*;

/**
 * One inventory slot and whether item pickup into it is blocked.
 *
 * <p>{@code slotId}s 0-8 are the player hotbar today, but the type is intentionally not
 * upper-bounded so it can be extended to other inventory slots later. A blocked slot is skipped
 * by vanilla's auto-pickup selection.
 */
public record SlotBlock(
        int slotId,
        boolean blocked) {

    /**
     * The currently-supported slot range: ids {@code [0, HOTBAR_SLOT_COUNT)} are the player hotbar.
     * Raise this when the model is extended to cover other inventory slots.
     */
    public static final int HOTBAR_SLOT_COUNT = 9;

    public SlotBlock {
        if (slotId < 0) {
            throw new IllegalArgumentException("slotId must be >= 0, was " + slotId);
        }
    }

    /**
     * Collects only the entries whose {@link #blocked()} is true, preserving iteration order. The
     * result is safe to query with {@code contains(new SlotBlock(id, true))} thanks to record
     * equality.
     *
     * @param slots the slots to scan.
     * @return a {@link LinkedHashSet} of the blocked-only entries.
     */
    public static Set<SlotBlock> blockedOnly(Collection<SlotBlock> slots) {
        Set<SlotBlock> result = new LinkedHashSet<>();
        if (slots != null) {
            for (SlotBlock slot : slots) {
                if (slot != null && slot.blocked()) {
                    result.add(slot);
                }
            }
        }
        return result;
    }

    /**
     * Builds the full explicit list of slots {@code 0..slotCount-1} in ascending id order, each
     * marked blocked when its blocked-entry is present in {@code blocked}. This is the shape used
     * for JSON persistence and the wire format.
     *
     * @param blocked the set of blocked-only entries.
     * @param slotCount the number of slots to enumerate.
     * @return the ordered full list of slots.
     */
    public static List<SlotBlock> fullList(Set<SlotBlock> blocked, int slotCount) {
        List<SlotBlock> slots = new ArrayList<>(Math.max(0, slotCount));
        for (int id = 0; id < slotCount; id++) {
            slots.add(new SlotBlock(id, blocked != null && blocked.contains(new SlotBlock(id, true))));
        }
        return slots;
    }
}
