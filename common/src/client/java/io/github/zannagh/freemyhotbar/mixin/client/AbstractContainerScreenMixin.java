package io.github.zannagh.freemyhotbar.mixin.client;

import io.github.zannagh.freemyhotbar.client.LockedSlots;
import io.github.zannagh.freemyhotbar.slot.GuiInteractionPolicy;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only input suppression: stops the player's own GUI actions from putting items into a
 * locked hotbar slot, in any container screen. Nothing is sent to the server — the interaction is
 * simply never started — so this carries no anti-cheat surface and works on vanilla servers.
 *
 * <p>Blocking is <b>inbound-only</b>: a locked slot refuses incoming items, but its contents can
 * always be taken out (plain click with an empty cursor, shift-click, drop key, double-click
 * collect), so the player is never trapped. See {@link GuiInteractionPolicy} for the per-click
 * rules and the known {@code quickMoveStack} gap.
 *
 * <p>Within one screen everything funnels through {@code slotClicked}: vanilla calls it for every
 * click, drag step, number-key swap, offhand-swap key and drop-key press. The extra
 * {@code mouseDragged} hook only keeps a locked slot out of the drag-distribution preview that
 * {@code slotClicked} would then reject anyway. Mouse scrolling needs no hook: vanilla's
 * {@code AbstractContainerScreen} does not handle scroll over slots at all.
 *
 * <p>This hook does <b>not</b> cover every screen. A subclass that overrides {@code slotClicked}
 * without calling {@code super} bypasses it entirely, and vanilla has exactly one such subclass:
 * {@code CreativeModeInventoryScreen}, handled separately by
 * {@link CreativeModeInventoryScreenMixin}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    protected boolean isQuickCrafting;

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void fmh$slotClicked(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (!LockedSlots.guardActive()) {
            return;
        }
        if (GuiInteractionPolicy.blocks(LockedSlots.describe(slot, button, type))) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void fmh$mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        if (!isQuickCrafting || !LockedSlots.guardActive()) {
            return;
        }
        if (LockedSlots.isLockedSlot(hoveredSlot)) {
            // Swallow this drag step so the locked slot is never added to the quick-craft set.
            cir.setReturnValue(true);
        }
    }
}
