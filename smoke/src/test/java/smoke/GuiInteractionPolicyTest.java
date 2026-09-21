package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zannagh.freemyhotbar.slot.GuiClick;
import io.github.zannagh.freemyhotbar.slot.GuiInteraction;
import io.github.zannagh.freemyhotbar.slot.GuiInteractionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The inbound-only GUI lock policy: items may never be placed into a locked hotbar slot by hand,
 * but taking them out always stays possible so the player cannot be trapped.
 */
class GuiInteractionPolicyTest {

    private static GuiClick click(GuiInteraction kind, boolean hoveredLocked, boolean hoveredHasItem,
            boolean carrying) {
        return new GuiClick(kind, hoveredLocked, hoveredHasItem, carrying, false, false);
    }

    @Test
    @DisplayName("placing a carried stack into a locked slot is blocked")
    void pickupIntoLockedSlotIsBlocked() {
        assertTrue(GuiInteractionPolicy.blocks(click(GuiInteraction.PICKUP, true, false, true)));
        assertTrue(GuiInteractionPolicy.blocks(click(GuiInteraction.PICKUP, true, true, true)));
    }

    @Test
    @DisplayName("taking a stack out of a locked slot is allowed")
    void pickupOutOfLockedSlotIsAllowed() {
        assertFalse(GuiInteractionPolicy.blocks(click(GuiInteraction.PICKUP, true, true, false)));
        assertFalse(GuiInteractionPolicy.blocks(click(GuiInteraction.QUICK_MOVE, true, true, false)));
        assertFalse(GuiInteractionPolicy.blocks(click(GuiInteraction.THROW, true, true, false)));
        assertFalse(GuiInteractionPolicy.blocks(click(GuiInteraction.PICKUP_ALL, true, true, true)));
    }

    @Test
    @DisplayName("unlocked slots are never touched")
    void unlockedSlotsAreNeverBlocked() {
        for (GuiInteraction kind : GuiInteraction.values()) {
            assertFalse(GuiInteractionPolicy.blocks(click(kind, false, true, true)),
                    kind + " must not be blocked on an unlocked slot");
        }
    }

    @Test
    @DisplayName("a drag-distribution step into a locked slot is blocked")
    void quickCraftIntoLockedSlotIsBlocked() {
        assertTrue(GuiInteractionPolicy.blocks(click(GuiInteraction.QUICK_CRAFT, true, false, true)));
    }

    @Test
    @DisplayName("creative middle-click copies out of a locked slot, so it is allowed")
    void cloneOutOfLockedSlotIsAllowed() {
        assertFalse(GuiInteractionPolicy.blocks(click(GuiInteraction.CLONE, true, true, false)));
    }

    @Test
    @DisplayName("a number-key swap is blocked from either end, but only when something would move in")
    void swapIsBlockedFromEitherEnd() {
        // hovered slot locked, the hotbar slot behind the number key holds something.
        assertTrue(GuiInteractionPolicy.blocks(
                new GuiClick(GuiInteraction.SWAP, true, false, false, false, true)));
        // hovered slot holds something, the hotbar slot behind the number key is locked.
        assertTrue(GuiInteractionPolicy.blocks(
                new GuiClick(GuiInteraction.SWAP, false, true, false, true, false)));
        // both ends empty of anything to give: nothing moves in, so nothing is blocked.
        assertFalse(GuiInteractionPolicy.blocks(
                new GuiClick(GuiInteraction.SWAP, true, false, false, true, false)));
    }

    @Test
    @DisplayName("the offhand swap key (button 40) names the offhand as the swap source")
    void offhandSwapKeyResolvesItsSource() {
        for (int button = 0; button < 9; button++) {
            assertEquals(button, GuiInteractionPolicy.swapSourceSlot(button),
                    "a number key swaps with the hotbar slot it names");
        }
        assertEquals(GuiInteractionPolicy.OFFHAND_SWAP_BUTTON,
                GuiInteractionPolicy.swapSourceSlot(GuiInteractionPolicy.OFFHAND_SWAP_BUTTON),
                "the offhand key's button doubles as Inventory slot 40, the offhand stack");
        assertEquals(-1, GuiInteractionPolicy.swapSourceSlot(-1));
        assertEquals(-1, GuiInteractionPolicy.swapSourceSlot(9), "9 names no hotbar slot");
        assertEquals(-1, GuiInteractionPolicy.swapSourceSlot(39));
        assertEquals(-1, GuiInteractionPolicy.swapSourceSlot(41));
    }

    @Test
    @DisplayName("an offhand swap into a locked slot is blocked when the offhand holds something")
    void offhandSwapIntoLockedSlotIsBlocked() {
        // Button 40 with a non-empty offhand: the shield would land in the locked slot.
        assertTrue(GuiInteractionPolicy.blocks(
                new GuiClick(GuiInteraction.SWAP, true, false, false, false, true)));
        // Empty offhand: pressing F over a locked slot only takes its stack out, so allow it.
        assertFalse(GuiInteractionPolicy.blocks(
                new GuiClick(GuiInteraction.SWAP, true, true, false, false, false)));
    }

    @Test
    @DisplayName("a null click is never blocked")
    void nullClickIsAllowed() {
        assertFalse(GuiInteractionPolicy.blocks(null));
    }
}
