package io.github.zannagh.freemyhotbar.fallback;

import java.util.Arrays;

/**
 * Remembers what every hotbar slot held before the latest change, so the client-side fallback can
 * tell an item that ARRIVED in a locked slot from the tool the player deliberately keeps there.
 *
 * <p><b>Why a baseline at all.</b> The evictor used to treat every occupied locked slot as the
 * target of the most recent pickup: one item landing in slot 5 armed a sweep that quick-moved the
 * sword out of slot 0 as well, which destroys the whole point of locking a slot. Only the
 * difference against a remembered state says which contents are new.
 *
 * <p><b>The rule this implements.</b> A locked slot's contents are evicted only when they were
 * introduced <i>without the player naming that slot</i>:
 * <ul>
 *   <li>An <b>arrival</b> is a slot that now holds more than the baseline says: a stack in a slot
 *       the baseline recorded as empty, a higher count of the same item, or a different item
 *       altogether. A stack that shrinks or stays put is never an arrival, so reserved tools and
 *       food are left alone no matter what happens in other slots.
 *   <li>An arrival is <b>explained</b>, and adopted into the baseline instead of evicted, when the
 *       player's own click named the slot as its destination — the {@code PICKUP}, {@code SWAP} and
 *       {@code QUICK_CRAFT} clicks the "Block my own clicks" setting governs. With that setting
 *       off, such a placement is the player's deliberate choice and must not be yanked back out;
 *       see {@link #notePlacement}.
 *   <li>Everything else is <b>unexplained</b> and armed for eviction: auto-pickup (which is the
 *       original case), and equally a shift-click in another container whose destination the
 *       server chose to be a locked hotbar slot. That second path sends no take-item packet and
 *       cannot be suppressed client-side at all, and it is caught here for free: the baseline does
 *       not care WHY the slot grew.
 * </ul>
 *
 * <p>Minecraft-free on purpose so the whole state machine can be unit-tested. Used from the client
 * thread only and not synchronized.
 */
public final class LockedSlotBaseline {

    /**
     * How long a player-named placement keeps a slot glued to its live contents, in client ticks
     * (two seconds). A placement is client-predicted, and the server's confirming slot update
     * arrives a few ticks later; the grace spans that gap.
     */
    public static final int PLACEMENT_GRACE_TICKS = 40;

    /**
     * How many gate-open ticks an unexplained arrival is pursued before the evictor gives up and
     * accepts it as the slot's new baseline, in client ticks (ten seconds).
     *
     * <p>Only ticks on which an eviction click could actually have been sent are counted, so a
     * player browsing a chest or sprinting across the map does not run the budget down without a
     * single attempt having been made.
     */
    public static final int PURSUIT_TICKS = 200;

    /** Item id standing for "nothing here"; matches vanilla's air. */
    private static final int EMPTY_ITEM = 0;

    private final int[] itemIds;
    private final int[] counts;
    private final int[] placementGrace;
    private final int[] pursuit;

    /**
     * Creates a baseline for a fixed number of hotbar slots.
     *
     * @param slotCount the number of slots to track; negative values are clamped to zero.
     */
    public LockedSlotBaseline(int slotCount) {
        int size = Math.max(0, slotCount);
        itemIds = new int[size];
        counts = new int[size];
        placementGrace = new int[size];
        pursuit = new int[size];
    }

    /**
     * Marks a slot as the destination the player just named with their own click, so the contents
     * that appear there are adopted rather than evicted.
     *
     * @param slot the hotbar slot index; out-of-range values are ignored.
     */
    public void notePlacement(int slot) {
        if (inRange(slot)) {
            placementGrace[slot] = PLACEMENT_GRACE_TICKS;
            pursuit[slot] = 0;
        }
    }

    /**
     * Feeds one slot's current contents in and reports whether they are an unexplained arrival.
     *
     * <p>Call exactly once per slot per client tick: the placement grace and the pursuit budget are
     * counted down from here, so calling it twice a tick runs both down twice as fast.
     *
     * @param slot the hotbar slot index; out-of-range values are never arrivals.
     * @param itemId the id of the item in the slot; anything when the slot is empty.
     * @param count the stack size in the slot, zero or less when the slot is empty.
     * @param gateOpen whether an eviction click could be sent on this tick; only then does the
     *     pursuit budget tick down.
     * @return true when the slot holds contents that arrived without the player naming it.
     */
    public boolean update(int slot, int itemId, int count, boolean gateOpen) {
        if (!inRange(slot)) {
            return false;
        }
        if (placementGrace[slot] > 0) {
            placementGrace[slot]--;
            accept(slot, itemId, count);
            return false;
        }
        if (!grew(slot, itemId, count)) {
            accept(slot, itemId, count);
            return false;
        }
        if (pursuit[slot] == 0) {
            pursuit[slot] = PURSUIT_TICKS;
        }
        if (gateOpen && --pursuit[slot] == 0) {
            // Ten seconds of chances came and went. Keeping the arrival outstanding forever would
            // re-arm a sweep on every tick for a slot nothing can be done about (a full inventory,
            // an exhausted retry budget), so it becomes the slot's new normal instead.
            accept(slot, itemId, count);
            return false;
        }
        return true;
    }

    /**
     * Adopts a slot's current contents as its baseline without any arrival check, e.g. after the
     * evictor has clicked it.
     *
     * @param slot the hotbar slot index; out-of-range values are ignored.
     * @param itemId the id of the item in the slot.
     * @param count the stack size in the slot.
     */
    public void accept(int slot, int itemId, int count) {
        if (!inRange(slot)) {
            return;
        }
        boolean empty = count <= 0;
        itemIds[slot] = empty ? EMPTY_ITEM : itemId;
        counts[slot] = empty ? 0 : count;
        pursuit[slot] = 0;
    }

    /** Drops all state; call when the player or the connection goes away. */
    public void reset() {
        Arrays.fill(itemIds, EMPTY_ITEM);
        Arrays.fill(counts, 0);
        Arrays.fill(placementGrace, 0);
        Arrays.fill(pursuit, 0);
    }

    private boolean grew(int slot, int itemId, int count) {
        if (count <= 0) {
            return false;
        }
        if (itemIds[slot] != itemId) {
            return true;
        }
        return count > counts[slot];
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < itemIds.length;
    }
}
