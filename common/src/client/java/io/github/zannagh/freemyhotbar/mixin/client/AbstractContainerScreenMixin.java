package io.github.zannagh.freemyhotbar.mixin.client;

import io.github.zannagh.freemyhotbar.client.LockedSlots;
import io.github.zannagh.freemyhotbar.slot.GuiInteraction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only input suppression: stops the player's own GUI actions from putting items into a
 * locked hotbar slot, in any container screen. Nothing is sent to the server — the interaction is
 * simply never started — so this carries no anti-cheat surface and works on vanilla servers.
 *
 * <p>Blocking is <b>inbound-only</b>: a locked slot refuses incoming items, but its contents can
 * always be taken out (plain click with an empty cursor, shift-click, drop key, double-click
 * collect), so the player is never trapped. See {@code GuiInteractionPolicy} for the per-click
 * rules and the {@code quickMoveStack} destination case it cannot reach.
 *
 * <p>With the setting OFF the same judgement is still made, for the opposite purpose: a click that
 * WOULD have been blocked is the player deliberately naming a locked slot, and
 * {@code LockedSlots.onSlotClicked} records it so the client-side evictor leaves what lands there
 * alone.
 *
 * <p>Within one screen everything funnels through {@code slotClicked}: vanilla calls it for every
 * click, drag step, number-key swap, offhand-swap key and drop-key press - including quick-craft
 * (click-drag) distribution, whose per-slot {@code QUICK_CRAFT} calls {@code mouseReleased} makes
 * one at a time over its collected slot set, so a locked slot is refused there like any other
 * click and never reaches the server. Mouse scrolling needs no hook: vanilla's
 * {@code AbstractContainerScreen} does not handle scroll over slots at all.
 *
 * <p>This hook does <b>not</b> cover every screen. A subclass that overrides {@code slotClicked}
 * without calling {@code super} bypasses it entirely, and vanilla has exactly one such subclass:
 * {@code CreativeModeInventoryScreen}, handled separately by
 * {@link CreativeModeInventoryScreenMixin}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    /**
     * The full descriptor of the {@code slotClicked} overload to hook.
     *
     * <p>A bare {@code "slotClicked"} is enough up to 26.2, but 26.3 gives
     * {@code AbstractContainerScreen} a SECOND, private {@code slotClicked} overload, and a bare
     * name then matches both - which Mixin reports as an ambiguous target rather than picking one.
     * Naming the descriptor here keeps the {@code @Inject} line itself invariant across the whole
     * span; only this constant moves.
     */
    @Unique
    //? if >= 26.3 {
    /*private static final String SLOT_CLICKED =
            "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V";
    *///?} else {
    private static final String SLOT_CLICKED = "slotClicked";
    //?}

    @Inject(method = SLOT_CLICKED, at = @At("HEAD"), cancellable = true)
    private void fmh$slotClicked(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (LockedSlots.onSlotClicked(slot, button, fmh$kindOf(type))) {
            ci.cancel();
        }
    }

    /**
     * Maps the game's click type onto the Minecraft-free {@link GuiInteraction}.
     *
     * <p>Deliberately repeated in both screen mixins rather than shared: this is the boundary the
     * vanilla enum is confined to, and a mixin cannot hold a shared static without putting that
     * class back on the mod side of the line.
     */
    private static GuiInteraction fmh$kindOf(ClickType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PICKUP -> GuiInteraction.PICKUP;
            case QUICK_MOVE -> GuiInteraction.QUICK_MOVE;
            case SWAP -> GuiInteraction.SWAP;
            case CLONE -> GuiInteraction.CLONE;
            case THROW -> GuiInteraction.THROW;
            case QUICK_CRAFT -> GuiInteraction.QUICK_CRAFT;
            case PICKUP_ALL -> GuiInteraction.PICKUP_ALL;
        };
    }
}
