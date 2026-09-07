package io.github.zannagh.freemyhotbar.mixin;

import io.github.zannagh.freemyhotbar.slot.SlotLockState;
import io.github.zannagh.freemyhotbar.slot.SlotSelection;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server pickup enforcement: when a player has locked hotbar slots, keep vanilla's
 * auto-pickup from selecting a locked slot. Uses plain Mixin injections (no MixinExtras) so it
 * compiles against the bare loader classpath.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    public NonNullList<ItemStack> items;

    @Shadow
    public Player player;

    @Shadow
    public int selected;

    @Shadow
    public abstract ItemStack getItem(int slot);

    @Shadow
    private boolean hasRemainingSpaceForItem(ItemStack dst, ItemStack src) {
        throw new AssertionError("shadow");
    }

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void fmh$getFreeSlot(CallbackInfoReturnable<Integer> cir) {
        int mask = SlotLockState.mask(player.getUUID());
        if (mask == 0) {
            return;
        }
        cir.setReturnValue(SlotSelection.firstAllowed(mask, items.size(), i -> items.get(i).isEmpty()));
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void fmh$getSlotWithRemainingSpace(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int mask = SlotLockState.mask(player.getUUID());
        if (mask == 0) {
            return;
        }
        if (!SlotSelection.isLocked(mask, selected) && hasRemainingSpaceForItem(getItem(selected), stack)) {
            cir.setReturnValue(selected);
            return;
        }
        if (hasRemainingSpaceForItem(getItem(40), stack)) {
            cir.setReturnValue(40);
            return;
        }
        cir.setReturnValue(SlotSelection.firstAllowed(mask, items.size(),
                i -> hasRemainingSpaceForItem(items.get(i), stack)));
    }
}
