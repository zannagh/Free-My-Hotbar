package smoke;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zannagh.freemyhotbar.fallback.LockedSlotBaseline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the rule the client-side fallback evicts by: only contents that ARRIVED in a locked slot
 * without the player naming it are moved out. The reserved tool a locked slot exists to hold must
 * survive anything that happens in the other eight.
 */
@DisplayName("Locked-slot arrival baseline")
class LockedSlotBaselineTest {

    private static final int SLOTS = 9;

    private static final int SWORD = 276;
    private static final int DIRT = 9;
    private static final int STONE = 1;

    /** One armed tick with the eviction gate open, the way the evictor calls it. */
    private static boolean tick(LockedSlotBaseline baseline, int slot, int itemId, int count) {
        return baseline.update(slot, itemId, count, true);
    }

    @Test
    @DisplayName("the player's sword survives a pickup into another locked slot")
    void reservedToolIsNeverEvicted() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        // Steady state: slot 0 holds the sword, slot 5 is empty. The evictor seeds the baseline
        // like this on every tick it cannot act (presence still UNKNOWN right after joining).
        baseline.accept(0, SWORD, 1);
        baseline.accept(5, 0, 0);

        // Dirt lands in slot 5. The sword has not moved, and must not be reported.
        assertFalse(tick(baseline, 0, SWORD, 1), "the sword is not an arrival and must be left alone");
        assertTrue(tick(baseline, 5, DIRT, 12), "the dirt arrived and must be evicted");

        // ...and it stays that way for as long as the dirt is stuck there.
        assertFalse(tick(baseline, 0, SWORD, 1), "the sword must survive a pending eviction too");
    }

    @Test
    @DisplayName("a locked slot that gains more of the same item reports the arrival")
    void growingStackIsAnArrival() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        baseline.accept(3, STONE, 10);

        assertFalse(tick(baseline, 3, STONE, 10), "an unchanged stack is not an arrival");
        assertTrue(tick(baseline, 3, STONE, 24), "a partial stack-up into the slot is an arrival");
    }

    @Test
    @DisplayName("a shrinking or replaced-by-nothing stack is never an arrival")
    void usingItemsUpIsNotAnArrival() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        baseline.accept(4, STONE, 32);

        assertFalse(tick(baseline, 4, STONE, 31), "eating or placing a block is not an arrival");
        assertFalse(tick(baseline, 4, 0, 0), "an emptied slot is not an arrival");
    }

    /**
     * The shift-click hole: a quick-move in another container can have a locked hotbar slot chosen
     * as its destination, server-side, with no take-item packet to announce it. The baseline does
     * not care what caused the slot to grow, so this needs no separate detection path.
     */
    @Test
    @DisplayName("an arrival with no pickup packet behind it is still an arrival")
    void unexplainedArrivalIsCaught() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        baseline.accept(7, 0, 0);

        assertTrue(tick(baseline, 7, STONE, 64));
    }

    @Test
    @DisplayName("a placement the player named is adopted, not yanked back out")
    void deliberatePlacementIsAdopted() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        baseline.accept(2, 0, 0);

        baseline.notePlacement(2);
        assertFalse(tick(baseline, 2, SWORD, 1), "the player put it there on purpose");

        // The grace window only spans the round trip to the server; once it lapses the adopted
        // contents are the new normal and are still not an arrival.
        for (int i = 0; i < LockedSlotBaseline.PLACEMENT_GRACE_TICKS; i++) {
            assertFalse(tick(baseline, 2, SWORD, 1));
        }
        assertTrue(tick(baseline, 2, DIRT, 5), "something else arriving afterwards still counts");
    }

    @Test
    @DisplayName("an arrival is pursued only on ticks an eviction click could have been sent")
    void pursuitOnlyRunsDownWhileTheGateIsOpen() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        baseline.accept(1, 0, 0);

        // Gate shut (a screen is open, or the player is sprinting): the arrival stays outstanding
        // however long it takes, because not one attempt has been made yet.
        for (int i = 0; i < LockedSlotBaseline.PURSUIT_TICKS * 2; i++) {
            assertTrue(baseline.update(1, DIRT, 3, false));
        }

        // With the gate open the budget runs out and the slot's contents become its new baseline,
        // so a hopeless arrival cannot re-arm a sweep on every tick forever.
        boolean stillArriving = true;
        for (int i = 0; i < LockedSlotBaseline.PURSUIT_TICKS && stillArriving; i++) {
            stillArriving = tick(baseline, 1, DIRT, 3);
        }
        assertFalse(stillArriving, "the pursuit budget must expire");
        assertFalse(tick(baseline, 1, DIRT, 3), "and the contents are the slot's new baseline");
        assertTrue(tick(baseline, 1, DIRT, 4), "a further arrival on top of it is caught again");
    }

    @Test
    @DisplayName("out-of-range slots are refused rather than throwing")
    void outOfRangeSlots() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        assertFalse(baseline.update(-1, DIRT, 1, true));
        assertFalse(baseline.update(SLOTS, DIRT, 1, true));
        baseline.accept(-1, DIRT, 1);
        baseline.notePlacement(SLOTS);
    }

    @Test
    @DisplayName("reset forgets everything, so a new connection re-seeds from scratch")
    void resetClearsEverything() {
        LockedSlotBaseline baseline = new LockedSlotBaseline(SLOTS);
        baseline.accept(0, SWORD, 1);
        assertFalse(tick(baseline, 0, SWORD, 1));

        baseline.reset();

        assertTrue(tick(baseline, 0, SWORD, 1), "a cleared baseline knows of no sword to spare");
    }
}
