package io.github.zannagh.freemyhotbar.client;

import io.github.zannagh.freemyhotbar.fallback.EquipDestination;
import io.github.zannagh.freemyhotbar.fallback.QuickMoveTargets;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the live inventory for {@link QuickMoveTargets}, which holds the actual decision.
 *
 * <p>Everything here is a lookup; the rules - the equipment branches winning over the
 * hotbar-to-main move, and what counts as room - live in the Minecraft-free core and are unit
 * tested there.
 *
 * <p>Every lookup below is deliberately spelled in the form that is present unchanged across the
 * whole supported game-version span, so this file needs no version gate at all:
 * {@code getEquipmentSlotForItem} through the entity rather than the static on {@code Mob} (which
 * moved in 1.21), {@link Player#getItemBySlot} rather than {@code Inventory.armor} /
 * {@code Inventory.offhand} (deleted in 1.21.5), and {@link Inventory#getItem} rather than the
 * {@code items} list (made private in 1.21.5).
 *
 * <p>The two calls 1.21 renamed without changing - the stack comparison and the humanoid-armour
 * slot type - are handled by the global replacement table in {@code stonecutter.gradle.kts}
 * rather than by a gate here. Sources are stored in the active variant's dialect, so both read
 * below exactly as the variant currently checked out spells them.
 */
final class EvictionTargets {

    private EvictionTargets() {
    }

    /**
     * Returns whether a quick-move would move the stack into the main inventory.
     *
     * @param player the local player whose locked slot is being drained.
     * @param stack the stack sitting in the locked slot.
     * @return true when the click would relocate the stack into the main inventory.
     */
    static boolean canRelocate(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        EquipDestination destination = destinationOf(slot);
        return QuickMoveTargets.canRelocate(
                destination,
                destination != EquipDestination.NONE && player.getItemBySlot(slot).isEmpty(),
                inventory.getContainerSize(),
                index -> accepts(inventory.getItem(index), stack));
    }

    /**
     * Maps the stack's equipment slot onto the branch {@code quickMoveStack} would take for it.
     *
     * <p>The armour test is the exact type comparison vanilla makes and must stay that way. It is
     * NOT {@code isArmor()}, which is true for animal armour and saddles as well - neither of
     * which vanilla's player quick-move can reach, so relaxing it would start reporting ordinary
     * cargo as unrelocatable.
     */
    private static EquipDestination destinationOf(EquipmentSlot slot) {
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            return EquipDestination.HUMANOID_ARMOR;
        }
        if (slot == EquipmentSlot.OFFHAND) {
            return EquipDestination.OFFHAND;
        }
        return EquipDestination.NONE;
    }

    private static boolean accepts(ItemStack target, ItemStack stack) {
        return QuickMoveTargets.slotAccepts(
                target.isEmpty(),
                stack.isStackable(),
                ItemStack.isSameItemSameTags(target, stack),
                target.getCount(),
                target.getMaxStackSize());
    }
}
