package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zannagh.freemyhotbar.slot.SlotSelection;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Plain-JVM unit tests for the pure lock-selection helper. Compiled from :common's main sources
 * (slot/ is included; **&#47;mixin/** is excluded), so no Minecraft classes are touched.
 */
@DisplayName("SlotSelection lock logic")
class SlotSelectionTest {

    private static final IntPredicate ALL_USABLE = i -> true;

    @Test
    @DisplayName("isLocked reflects the mask bit for in-range hotbar slots")
    void isLockedReadsMaskBits() {
        int mask = 0b000000101; // slots 0 and 2 locked
        assertTrue(SlotSelection.isLocked(mask, 0), "bit 0 set -> slot 0 locked");
        assertFalse(SlotSelection.isLocked(mask, 1), "bit 1 clear -> slot 1 unlocked");
        assertTrue(SlotSelection.isLocked(mask, 2), "bit 2 set -> slot 2 locked");
        assertFalse(SlotSelection.isLocked(mask, 8), "bit 8 clear -> slot 8 unlocked");
    }

    @Test
    @DisplayName("isLocked is false for out-of-range slots regardless of mask")
    void isLockedRejectsOutOfRange() {
        int mask = 0x1FF; // every hotbar bit set
        assertFalse(SlotSelection.isLocked(mask, -1), "negative slot is never locked");
        assertFalse(SlotSelection.isLocked(mask, 9), "slot 9 is outside the hotbar");
        assertFalse(SlotSelection.isLocked(mask, 40), "offhand-style index is never locked");
    }

    @Test
    @DisplayName("firstAllowed returns the first usable, unlocked index")
    void firstAllowedPicksFirstUsableUnlocked() {
        int mask = 0b000000011; // slots 0 and 1 locked
        assertEquals(2, SlotSelection.firstAllowed(mask, 9, ALL_USABLE),
                "slots 0 and 1 locked -> first allowed is 2");
    }

    @Test
    @DisplayName("firstAllowed skips locked slots even when they are usable")
    void firstAllowedSkipsLockedSlots() {
        int mask = 0b000000001; // slot 0 locked
        // Only slots 0 and 3 are usable; 0 is locked so 3 wins.
        IntPredicate usable = i -> i == 0 || i == 3;
        assertEquals(3, SlotSelection.firstAllowed(mask, 9, usable),
                "usable-but-locked slot 0 is skipped in favour of slot 3");
    }

    @Test
    @DisplayName("firstAllowed returns -1 when every usable slot is locked")
    void firstAllowedReturnsMinusOneWhenAllUsableAreLocked() {
        int mask = 0b000000011; // slots 0 and 1 locked
        IntPredicate usable = i -> i == 0 || i == 1;
        assertEquals(-1, SlotSelection.firstAllowed(mask, 9, usable),
                "all usable slots locked -> no allowed slot");
    }

    @Test
    @DisplayName("firstAllowed returns -1 when no slot is usable")
    void firstAllowedReturnsMinusOneWhenNothingUsable() {
        assertEquals(-1, SlotSelection.firstAllowed(0, 9, i -> false),
                "no usable slot -> -1 even with an empty mask");
    }
}
