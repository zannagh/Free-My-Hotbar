package io.github.zannagh.freemyhotbar.mixin.client;

import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code CreativeModeInventoryScreen$SlotWrapper.target}, the slot a creative Inventory-tab
 * slot really stands for.
 *
 * <p>The wrapper is constructed as {@code new SlotWrapper(inventoryMenu.slots.get(i), i, x, y)} and
 * passes {@code target.container} plus the <i>menu</i> index {@code i} to {@code Slot}'s
 * constructor. Its {@code getContainerSlot()} therefore reports the {@code InventoryMenu} index
 * (0-45), not the index into the player's {@code Inventory} — so a wrapped hotbar slot looks like
 * container index 36-44 and, worse, a wrapped armor slot looks like container index 5-8, which a
 * naive check reads as hotbar slots 5-8. Unwrapping first is what keeps
 * {@code LockedSlots.hotbarIndex} honest inside the creative screen.
 *
 * <p>The class is package-private in vanilla, hence the string target.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface CreativeSlotWrapperAccessor {

    /**
     * Returns the slot this wrapper delegates to.
     *
     * @return the wrapped {@code InventoryMenu} slot.
     */
    @Accessor("target")
    Slot freeMyHotbar$getTarget();
}
