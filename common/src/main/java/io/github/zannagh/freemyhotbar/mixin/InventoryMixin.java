package io.github.zannagh.freemyhotbar.mixin;

import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import io.github.zannagh.freemyhotbar.slot.SlotLockState;
import io.github.zannagh.freemyhotbar.slot.SlotSelection;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server pickup enforcement: when a player has locked hotbar slots, keep vanilla's
 * auto-pickup from selecting a locked slot. Uses plain Mixin injections (no MixinExtras) so it
 * compiles against the bare loader classpath.
 *
 * <p>The {@code @Shadow} declarations below are deliberately NOT version-gated even though
 * {@code items} and {@code selected} are public up to 1.21.4 and private from 1.21.5 on. Mixin
 * 0.8.5 validates exactly three things about a shadow FIELD - that it exists in the target, that
 * it is not also {@code @Unique}, and that its STATIC modifier matches ({@code Bytecode
 * .compareFlags(..., ACC_STATIC)}); a {@code @Final} mismatch is a verbose-log warning and access
 * modifiers are not compared at all. The mixin is merged into {@code Inventory} itself, so the
 * rewritten field access is legal whatever the target's visibility. All six shadowed members keep
 * their names unchanged from 1.20.1 through 26.3, which IS the part Mixin hard-fails on.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    @Shadow
    @Final
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
        Set<SlotBlock> blocked = SlotLockState.blocked(player.getUUID());
        if (blocked.isEmpty()) {
            return;
        }
        cir.setReturnValue(SlotSelection.firstAllowed(blocked, items.size(), i -> items.get(i).isEmpty()));
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void fmh$getSlotWithRemainingSpace(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Set<SlotBlock> blocked = SlotLockState.blocked(player.getUUID());
        if (blocked.isEmpty()) {
            return;
        }
        if (!SlotSelection.isBlocked(blocked, selected)
                && hasRemainingSpaceForItem(getItem(selected), stack)) {
            cir.setReturnValue(selected);
            return;
        }
        if (hasRemainingSpaceForItem(getItem(40), stack)) {
            cir.setReturnValue(40);
            return;
        }
        cir.setReturnValue(SlotSelection.firstAllowed(blocked, items.size(),
                i -> hasRemainingSpaceForItem(items.get(i), stack)));
    }
}
