package io.github.zannagh.freemyhotbar.slot;

/**
 * Decides whether a manual slot interaction would put an item INTO a locked hotbar slot and must
 * therefore be suppressed.
 *
 * <p><b>Semantics: inbound-only.</b> A locked slot rejects incoming items but never holds its
 * contents hostage. Taking a stack out of a locked slot, shift-clicking it away, dropping it and
 * double-click-collecting from it all stay available, so the player can never be trapped: whatever
 * is in a locked slot can always be removed by hand (and, outside any screen, simply selected and
 * used or dropped). Only the "put something in" direction is blocked, which is exactly what the
 * setting is labelled as ("Block my own clicks into locked slots").
 *
 * <p><b>Shift-click is deliberately not blocked here, in either direction.</b> A
 * {@code QUICK_MOVE} on a locked slot is purely outbound. A {@code QUICK_MOVE} on some OTHER slot
 * is a different matter: the menu's own {@code quickMoveStack} resolves the destination and may
 * well pick a locked hotbar slot. That choice is server-authoritative, is not made in the screen
 * at all, and therefore cannot be suppressed from client input handling on a server without the
 * mod — blocking the click outright would only stop the player from moving their own items.
 *
 * <p>The client-side fallback covers it instead, and needs no help from this class to do so: it
 * evicts by comparing each locked slot against a per-slot baseline, so anything that ARRIVES
 * unasked is moved out whether an auto-pickup or a shift-click destination put it there. The one
 * thing it must not undo is a placement the player asked for by name — the {@code PICKUP},
 * {@code SWAP} and {@code QUICK_CRAFT} clicks judged below, when the setting lets them through.
 * Those are recorded as deliberate and adopted rather than evicted.
 */
public final class GuiInteractionPolicy {

    /**
     * The button vanilla passes for the offhand swap key (F by default).
     *
     * <p>{@code AbstractContainerScreen.checkHotbarKeyPressed} dispatches that key as
     * {@code slotClicked(hoveredSlot, slotId, 40, SWAP)} - the same click type as a number key, but
     * with a button far outside the hotbar range. 40 is the offhand's index in the player's
     * {@code Inventory}, so it doubles as the source slot to read.
     */
    public static final int OFFHAND_SWAP_BUTTON = 40;

    private GuiInteractionPolicy() {
    }

    /**
     * Returns the player-inventory slot a {@link GuiInteraction#SWAP} would take its incoming stack
     * from.
     *
     * @param button the vanilla button argument of the swap click.
     * @return the inventory slot index (0-8 for a number key, 40 for the offhand key), or -1 when
     *     the button names no swap source.
     */
    public static int swapSourceSlot(int button) {
        if (button >= 0 && button < SlotBlock.HOTBAR_SLOT_COUNT) {
            return button;
        }
        return button == OFFHAND_SWAP_BUTTON ? OFFHAND_SWAP_BUTTON : -1;
    }

    /**
     * Returns whether the described interaction would move an item into a locked hotbar slot.
     *
     * @param click the interaction to judge; null is never blocked.
     * @return true when the interaction must be cancelled.
     */
    public static boolean blocks(GuiClick click) {
        if (click == null || click.kind() == null) {
            return false;
        }
        return switch (click.kind()) {
            // Only blocked while carrying something — an empty cursor is taking the stack out.
            case PICKUP -> click.hoveredLocked() && click.carryingItem();
            // One step of a click-and-drag always deposits into the slot it passes over.
            case QUICK_CRAFT -> click.hoveredLocked();
            // A swap is blocked when either end is locked and the other end has something to give.
            case SWAP -> (click.hoveredLocked() && click.swapSourceHasItem())
                    || (click.swapSourceLocked() && click.hoveredHasItem());
            // Purely outbound from the clicked slot. CLONE belongs here too: vanilla only honours
            // it with an empty cursor, copying the hovered stack out, never putting anything in.
            case QUICK_MOVE, THROW, PICKUP_ALL, CLONE -> false;
        };
    }
}
