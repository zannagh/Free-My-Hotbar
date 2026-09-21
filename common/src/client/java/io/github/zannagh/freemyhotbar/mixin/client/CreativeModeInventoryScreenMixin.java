package io.github.zannagh.freemyhotbar.mixin.client;

import io.github.zannagh.freemyhotbar.client.LockedSlots;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The same inbound-only lock policy as {@link AbstractContainerScreenMixin}, applied again for the
 * creative inventory.
 *
 * <p>A second hook is unavoidable: {@code CreativeModeInventoryScreen} overrides
 * {@code slotClicked} and never calls {@code super.slotClicked} anywhere in its body, so the hook
 * on {@code AbstractContainerScreen} simply never runs while the creative screen is open and every
 * click into a locked slot would go through unblocked.
 *
 * <p>Both of the creative screen's slot layouts resolve correctly through
 * {@code LockedSlots.hotbarIndex}: the item tabs build their hotbar row as plain
 * {@code Slot(playerInventory, 0..8)}, and the Inventory tab's wrappers are unwrapped by
 * {@link CreativeSlotWrapperAccessor}.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void fmh$slotClicked(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (LockedSlots.onSlotClicked(slot, button, type)) {
            ci.cancel();
        }
    }
}
