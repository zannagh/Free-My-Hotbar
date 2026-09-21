package io.github.zannagh.freemyhotbar.fallback;

/**
 * What the client-side fallback should do with one item that already sits in a locked hotbar slot.
 */
public enum EvictionAction {

    /** Leave the item where it is. */
    NONE,

    /** Quick-move the stack out of the hotbar slot. */
    MOVE,

    /** Throw the whole stack on the ground. */
    DROP
}
