package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import io.github.zannagh.freemyhotbar.slot.SlotSelection;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Plain-JVM unit tests for the pure slot-selection helper and the {@link SlotBlock} model. Compiled
 * from :common's main sources (slot/ is included; **&#47;mixin/** is excluded), so no Minecraft
 * classes are touched.
 */
@DisplayName("SlotSelection block logic")
class SlotSelectionTest {

    private static final IntPredicate ALL_USABLE = i -> true;

    private static SlotBlock blocked(int slot) {
        return new SlotBlock(slot, true);
    }

    @Test
    @DisplayName("isBlocked reflects membership for in-range hotbar slots")
    void isBlockedReadsMembership() {
        Set<SlotBlock> blocked = Set.of(blocked(0), blocked(2)); // slots 0 and 2 blocked
        assertTrue(SlotSelection.isBlocked(blocked, 0), "0 present -> slot 0 blocked");
        assertFalse(SlotSelection.isBlocked(blocked, 1), "1 absent -> slot 1 unblocked");
        assertTrue(SlotSelection.isBlocked(blocked, 2), "2 present -> slot 2 blocked");
        assertFalse(SlotSelection.isBlocked(blocked, 8), "8 absent -> slot 8 unblocked");
    }

    @Test
    @DisplayName("isBlocked is false for ids not in the set")
    void isBlockedRejectsOutOfRange() {
        // every hotbar slot blocked
        Set<SlotBlock> blocked = Set.of(blocked(0), blocked(1), blocked(2), blocked(3), blocked(4),
                blocked(5), blocked(6), blocked(7), blocked(8));
        assertFalse(SlotSelection.isBlocked(blocked, -1), "negative slot is never blocked");
        assertFalse(SlotSelection.isBlocked(blocked, 9), "slot 9 is outside the hotbar");
        assertFalse(SlotSelection.isBlocked(blocked, 40), "offhand-style index is never blocked");
    }

    @Test
    @DisplayName("firstAllowed returns the first usable, unblocked index")
    void firstAllowedPicksFirstUsableUnblocked() {
        Set<SlotBlock> blocked = Set.of(blocked(0), blocked(1)); // slots 0 and 1 blocked
        assertEquals(2, SlotSelection.firstAllowed(blocked, 9, ALL_USABLE),
                "slots 0 and 1 blocked -> first allowed is 2");
    }

    @Test
    @DisplayName("firstAllowed skips blocked slots even when they are usable")
    void firstAllowedSkipsBlockedSlots() {
        Set<SlotBlock> blocked = Set.of(blocked(0)); // slot 0 blocked
        // Only slots 0 and 3 are usable; 0 is blocked so 3 wins.
        IntPredicate usable = i -> i == 0 || i == 3;
        assertEquals(3, SlotSelection.firstAllowed(blocked, 9, usable),
                "usable-but-blocked slot 0 is skipped in favour of slot 3");
    }

    @Test
    @DisplayName("firstAllowed returns -1 when every usable slot is blocked")
    void firstAllowedReturnsMinusOneWhenAllUsableAreBlocked() {
        Set<SlotBlock> blocked = Set.of(blocked(0), blocked(1)); // slots 0 and 1 blocked
        IntPredicate usable = i -> i == 0 || i == 1;
        assertEquals(-1, SlotSelection.firstAllowed(blocked, 9, usable),
                "all usable slots blocked -> no allowed slot");
    }

    @Test
    @DisplayName("firstAllowed returns -1 when no slot is usable")
    void firstAllowedReturnsMinusOneWhenNothingUsable() {
        assertEquals(-1, SlotSelection.firstAllowed(Set.of(), 9, i -> false),
                "no usable slot -> -1 even with no blocked slots");
    }

    @Test
    @DisplayName("blockedOnly keeps only the blocked entries")
    void blockedOnlyKeepsBlockedEntries() {
        List<SlotBlock> mixed = List.of(
                new SlotBlock(0, false),
                new SlotBlock(1, true),
                new SlotBlock(2, false),
                new SlotBlock(3, true));
        Set<SlotBlock> result = SlotBlock.blockedOnly(mixed);
        assertEquals(Set.of(blocked(1), blocked(3)), result,
                "only the blocked=true entries survive");
    }

    @Test
    @DisplayName("fullList enumerates 0..slotCount-1 marking blocked entries")
    void fullListMarksBlocked() {
        List<SlotBlock> slots = SlotBlock.fullList(Set.of(blocked(1), blocked(3)), 5);
        assertEquals(5, slots.size(), "fullList enumerates every slot up to slotCount");
        assertEquals(new SlotBlock(0, false), slots.get(0), "slot 0 not blocked");
        assertEquals(new SlotBlock(1, true), slots.get(1), "slot 1 blocked");
        assertEquals(new SlotBlock(3, true), slots.get(3), "slot 3 blocked");
        assertEquals(new SlotBlock(4, false), slots.get(4), "slot 4 not blocked");
    }

    @Test
    @DisplayName("blockedOnly and fullList round-trip the blocked set")
    void blockedOnlyFullListRoundTrips() {
        Set<SlotBlock> original = Set.of(blocked(0), blocked(2), blocked(8));
        Set<SlotBlock> roundTripped = SlotBlock.blockedOnly(SlotBlock.fullList(original, 9));
        assertEquals(original, roundTripped,
                "blockedOnly(fullList(x)) recovers the blocked entries");
    }
}
