package io.github.zannagh.freemyhotbar.client;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Predicts whether a quick-move out of a hotbar slot would land the stack in the main inventory.
 *
 * <p>Needed because {@code AbstractContainerMenu.moveItemStackTo} returns false silently when
 * there is nowhere to put the stack: the click is accepted, nothing happens and no packet says so.
 * Asking up-front is what keeps the evictor from clicking the same slot forever and what decides
 * whether the drop fallback kicks in.
 */
final class EvictionTargets {

    /** First slot of the main inventory in {@code Inventory.items}; 0-8 are the hotbar. */
    private static final int MAIN_START = 9;

    /** One past the last main-inventory slot in {@code Inventory.items}. */
    private static final int MAIN_END = 36;

    private EvictionTargets() {
    }

    /**
     * Returns whether a quick-move would move the stack into the main inventory.
     *
     * <p>Deliberately narrower than {@code InventoryMenu.quickMoveStack}, which tests the armor
     * slots ({@code 8 - index}) and the offhand slot (45) BEFORE the hotbar-to-main branch. For a
     * helmet, chestplate, elytra, mob head, carved pumpkin or shield with its equipment slot free,
     * a quick-move therefore EQUIPS the item rather than moving it - which would silently dress the
     * player in whatever they walked over, blind them behind a pumpkin, or, with Curse of Binding,
     * lock the piece on until they die. The mod must never do that on its own, so a stack that
     * would hit an equipment branch is reported as NOT relocatable: {@code MOVE} then leaves it in
     * the locked slot (honest and safe) and {@code MOVE_OR_DROP} falls through to the bounded drop.
     *
     * @param inventory the player's inventory.
     * @param stack the stack sitting in the locked slot.
     * @return true when the click would relocate the stack into the main inventory.
     */
    static boolean canRelocate(Inventory inventory, ItemStack stack) {
        if (inventory == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (wouldEquip(inventory, stack)) {
            return false;
        }
        return hasRoomInMain(inventory, stack);
    }

    /**
     * Mirrors the two branches {@code InventoryMenu.quickMoveStack} takes before it ever considers
     * the hotbar-to-main move: armor when {@code 8 - equipmentSlot.getIndex()} is free, offhand
     * when menu slot 45 is free. Both are reached purely from the stack's equipment slot, so a
     * quick-move of such a stack equips it no matter how much room the main inventory has.
     */
    private static boolean wouldEquip(Inventory inventory, ItemStack stack) {
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            return inventory.armor.get(slot.getIndex()).isEmpty();
        }
        if (slot == EquipmentSlot.OFFHAND) {
            return inventory.offhand.get(0).isEmpty();
        }
        return false;
    }

    private static boolean hasRoomInMain(Inventory inventory, ItemStack stack) {
        for (int index = MAIN_START; index < MAIN_END && index < inventory.items.size(); index++) {
            ItemStack target = inventory.items.get(index);
            if (target.isEmpty()) {
                return true;
            }
            if (stack.isStackable()
                    && ItemStack.isSameItemSameTags(target, stack)
                    && target.getCount() < target.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }
}
