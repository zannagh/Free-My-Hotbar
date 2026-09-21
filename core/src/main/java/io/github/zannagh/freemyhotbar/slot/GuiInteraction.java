package io.github.zannagh.freemyhotbar.slot;

/**
 * Loader- and Minecraft-free mirror of vanilla's {@code ClickType}, so the GUI-blocking policy can
 * live in the MC-free core and be unit-tested. The client maps the game's enum onto this one.
 */
public enum GuiInteraction {

    /** Plain left/right click: place, take or swap the carried stack. */
    PICKUP,
    /** Shift-click: move the clicked slot's stack elsewhere in the menu. */
    QUICK_MOVE,
    /** Number key (or offhand key) while hovering a slot: swap with that hotbar slot. */
    SWAP,
    /** Creative middle-click: copy the hovered stack onto the cursor. */
    CLONE,
    /** Drop key, or a click outside the window: throw a stack on the ground. */
    THROW,
    /** One step of a click-and-drag distribution of the carried stack. */
    QUICK_CRAFT,
    /** Double-click: collect matching items from the menu onto the cursor. */
    PICKUP_ALL
}
