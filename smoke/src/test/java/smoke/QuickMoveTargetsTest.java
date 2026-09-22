package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zannagh.freemyhotbar.fallback.EquipDestination;
import io.github.zannagh.freemyhotbar.fallback.QuickMoveTargets;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the quick-move destination prediction the client-side evictor relies on.
 *
 * <p>These cases were written against the behaviour of the original 1.20.1-only
 * {@code EvictionTargets} (static {@code getEquipmentSlotForItem}, {@code inventory.armor} /
 * {@code inventory.offhand} / {@code inventory.items}, {@code isSameItemSameTags}) so that the
 * later swap to the version-invariant Minecraft APIs could be shown to preserve semantics rather
 * than merely to compile.
 */
@DisplayName("Quick-move destination prediction")
class QuickMoveTargetsTest {

    /** Inventory size the 1.20.1 code saw through {@code inventory.items.size()}. */
    private static final int ITEMS_SIZE = 36;

    /** Every main-inventory slot is full and holds something unrelated. */
    private static final IntPredicate MAIN_FULL = index -> false;

    /** The first main-inventory slot is free. */
    private static final IntPredicate MAIN_HAS_ROOM = index -> index == QuickMoveTargets.MAIN_START;

    @Test
    @DisplayName("the main-inventory window is slots 9-35, hotbar and armour excluded")
    void mainWindowBounds() {
        assertEquals(9, QuickMoveTargets.MAIN_START);
        assertEquals(36, QuickMoveTargets.MAIN_END);

        assertFalse(QuickMoveTargets.hasRoomInMain(ITEMS_SIZE, index -> index < QuickMoveTargets.MAIN_START),
                "a free hotbar slot is not a quick-move destination");
        assertFalse(QuickMoveTargets.hasRoomInMain(ITEMS_SIZE, index -> index >= QuickMoveTargets.MAIN_END),
                "nothing past the main inventory counts, whatever the container reports as its size");
        assertTrue(QuickMoveTargets.hasRoomInMain(ITEMS_SIZE, index -> index == 35),
                "the last main slot must still be found");
    }

    @Test
    @DisplayName("a smaller inventory than the window bounds the scan instead of overrunning it")
    void shortInventoryBoundsTheScan() {
        assertFalse(QuickMoveTargets.hasRoomInMain(9, index -> true), "no main slots exist at all");
        assertTrue(QuickMoveTargets.hasRoomInMain(10, index -> index == 9));
        assertFalse(QuickMoveTargets.hasRoomInMain(10, index -> index == 10),
                "a slot past the reported size must never be probed");
    }

    @Test
    @DisplayName("a free armour or offhand slot means vanilla would EQUIP the stack, not move it")
    void equipBranchesWinOverTheMainInventory() {
        assertTrue(QuickMoveTargets.wouldEquip(EquipDestination.HUMANOID_ARMOR, true));
        assertTrue(QuickMoveTargets.wouldEquip(EquipDestination.OFFHAND, true));

        // The whole point: room in the main inventory does NOT rescue a wearable stack, because
        // quickMoveStack tests the equipment branches first. This is what stops a Curse of Binding
        // helmet the player walked over from being auto-worn by the evictor.
        assertFalse(QuickMoveTargets.canRelocate(EquipDestination.HUMANOID_ARMOR, true, ITEMS_SIZE, MAIN_HAS_ROOM),
                "a helmet with a free head slot must never be reported as relocatable");
        assertFalse(QuickMoveTargets.canRelocate(EquipDestination.OFFHAND, true, ITEMS_SIZE, MAIN_HAS_ROOM),
                "a shield with a free offhand must never be reported as relocatable");
    }

    @Test
    @DisplayName("an occupied equipment slot puts the stack back on the ordinary move path")
    void occupiedEquipmentSlotFallsThrough() {
        assertFalse(QuickMoveTargets.wouldEquip(EquipDestination.HUMANOID_ARMOR, false));
        assertFalse(QuickMoveTargets.wouldEquip(EquipDestination.OFFHAND, false));

        assertTrue(QuickMoveTargets.canRelocate(EquipDestination.HUMANOID_ARMOR, false, ITEMS_SIZE, MAIN_HAS_ROOM),
                "a helmet is just an item once the head slot is taken");
        assertFalse(QuickMoveTargets.canRelocate(EquipDestination.HUMANOID_ARMOR, false, ITEMS_SIZE, MAIN_FULL),
                "... and then it still needs somewhere to go");
    }

    @Test
    @DisplayName("a non-equipment stack only needs room in the main inventory")
    void plainStacks() {
        assertFalse(QuickMoveTargets.wouldEquip(EquipDestination.NONE, true),
                "an empty slot flag must not equip a stack that has no equipment branch");
        assertFalse(QuickMoveTargets.wouldEquip(null, true), "an unknown destination never equips");

        assertTrue(QuickMoveTargets.canRelocate(EquipDestination.NONE, true, ITEMS_SIZE, MAIN_HAS_ROOM));
        assertFalse(QuickMoveTargets.canRelocate(EquipDestination.NONE, false, ITEMS_SIZE, MAIN_FULL),
                "a full main inventory means the quick-move would silently do nothing");
    }

    @Test
    @DisplayName("an empty target slot always has room; a full or foreign one never does")
    void slotAcceptance() {
        assertTrue(QuickMoveTargets.slotAccepts(true, false, false, 0, 64),
                "an empty slot takes anything, stackable or not");

        assertFalse(QuickMoveTargets.slotAccepts(false, false, true, 1, 64),
                "an unstackable stack cannot be merged into an occupied slot");
        assertFalse(QuickMoveTargets.slotAccepts(false, true, false, 1, 64),
                "a different item (or different data) is not a merge target");
        assertFalse(QuickMoveTargets.slotAccepts(false, true, true, 64, 64),
                "a full stack of the same item has no room left");

        assertTrue(QuickMoveTargets.slotAccepts(false, true, true, 63, 64),
                "one free item of headroom is enough");
    }
}
