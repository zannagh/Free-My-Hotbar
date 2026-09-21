package io.github.zannagh.freemyhotbar.slot;

/**
 * An MC-free description of one slot interaction in a container screen, reduced to the facts the
 * lock policy needs. Built on the client from the live screen state and fed to
 * {@link GuiInteractionPolicy}.
 *
 * @param kind what the interaction would do.
 * @param hoveredLocked true when the slot under the cursor is a locked hotbar slot.
 * @param hoveredHasItem true when the slot under the cursor holds a stack.
 * @param carryingItem true when the cursor currently carries a stack.
 * @param swapSourceLocked for {@link GuiInteraction#SWAP}, true when the hotbar slot named by the
 *     pressed number key is locked; false for every other kind.
 * @param swapSourceHasItem for {@link GuiInteraction#SWAP}, true when that hotbar slot holds a
 *     stack; false for every other kind.
 */
public record GuiClick(
        GuiInteraction kind,
        boolean hoveredLocked,
        boolean hoveredHasItem,
        boolean carryingItem,
        boolean swapSourceLocked,
        boolean swapSourceHasItem) {
}
