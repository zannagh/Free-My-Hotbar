package io.github.zannagh.freemyhotbar.fallback;

import java.util.function.IntPredicate;

/**
 * The pure decision behind "would a quick-move out of a locked hotbar slot actually relocate this
 * stack into the main inventory?", free of any Minecraft type so it can be unit-tested directly.
 *
 * <p>It mirrors {@code InventoryMenu.quickMoveStack}, whose branch order is what makes the
 * question non-obvious: the armour slots and the offhand slot are evaluated BEFORE the
 * hotbar-to-main move. For a helmet, chestplate, elytra, mob head, carved pumpkin or shield whose
 * equipment slot is free, a quick-move therefore EQUIPS the item rather than moving it - which
 * would silently dress the player in whatever they walked over, blind them behind a pumpkin, or,
 * with Curse of Binding, lock the piece on until they die. Such a stack is reported as NOT
 * relocatable so the caller leaves it where it is instead.
 *
 * <p>The other half of the question matters because a failed quick-move is silent:
 * {@code moveItemStackTo} returns false with no packet to say so, so the click is accepted and
 * nothing happens. Asking up-front is what keeps the evictor from clicking the same slot forever.
 */
public final class QuickMoveTargets {

    /** First slot of the main inventory in the player's inventory; 0-8 are the hotbar. */
    public static final int MAIN_START = 9;

    /** One past the last main-inventory slot in the player's inventory. */
    public static final int MAIN_END = 36;

    private QuickMoveTargets() {
    }

    /**
     * Returns whether a quick-move would move the stack into the main inventory.
     *
     * <p>The equipment branches are tested first, exactly as vanilla orders them: a stack that
     * would be equipped never reaches the hotbar-to-main move, however much room there is.
     *
     * @param destination the equipment branch the stack's equipment slot selects.
     * @param equipmentSlotEmpty whether that equipment slot is currently free; ignored for
     *     {@link EquipDestination#NONE}.
     * @param inventorySize the size of the player's inventory; only the main-inventory window is
     *     scanned, so a larger container size simply bounds the loop earlier than the window does.
     * @param mainSlotAccepts whether the given inventory index could take (part of) the stack.
     * @return true when the click would relocate the stack into the main inventory.
     */
    public static boolean canRelocate(EquipDestination destination, boolean equipmentSlotEmpty,
            int inventorySize, IntPredicate mainSlotAccepts) {
        if (wouldEquip(destination, equipmentSlotEmpty)) {
            return false;
        }
        return hasRoomInMain(inventorySize, mainSlotAccepts);
    }

    /**
     * Returns whether a quick-move would equip the stack rather than move it.
     *
     * @param destination the equipment branch the stack's equipment slot selects.
     * @param equipmentSlotEmpty whether that equipment slot is currently free.
     * @return true when vanilla would put the stack on the player.
     */
    public static boolean wouldEquip(EquipDestination destination, boolean equipmentSlotEmpty) {
        if (destination == null || destination == EquipDestination.NONE) {
            return false;
        }
        return equipmentSlotEmpty;
    }

    /**
     * Returns whether any main-inventory slot could take the stack.
     *
     * @param inventorySize the size of the player's inventory.
     * @param mainSlotAccepts whether the given inventory index could take (part of) the stack.
     * @return true when at least one slot in the main-inventory window has room.
     */
    public static boolean hasRoomInMain(int inventorySize, IntPredicate mainSlotAccepts) {
        for (int index = MAIN_START; index < MAIN_END && index < inventorySize; index++) {
            if (mainSlotAccepts.test(index)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether one target slot could take (part of) the moved stack.
     *
     * @param targetEmpty whether the target slot is empty.
     * @param stackable whether the moved stack may be merged at all.
     * @param sameItem whether the target slot holds the same item with the same data.
     * @param targetCount how many items the target slot holds.
     * @param targetMaxStackSize the target slot's stack limit.
     * @return true when the slot has room for the moved stack.
     */
    public static boolean slotAccepts(boolean targetEmpty, boolean stackable, boolean sameItem,
            int targetCount, int targetMaxStackSize) {
        if (targetEmpty) {
            return true;
        }
        return stackable && sameItem && targetCount < targetMaxStackSize;
    }
}
