package io.github.zannagh.freemyhotbar.fallback;

import io.github.zannagh.freemyhotbar.config.FallbackMode;

/**
 * Pure decisions behind the client-side eviction of items from locked hotbar slots. Kept free of
 * any Minecraft type so the rules can be unit-tested directly.
 *
 * <p>The movement gate below is the default, not a law: {@link #clickGateOpen} lets the player's
 * {@code evictImmediately} setting bypass it.
 */
public final class EvictionPolicy {

    /**
     * How often the same unchanged stack may be clicked before the evictor gives up on it.
     *
     * <p>A quick-move into a full inventory is a completely silent no-op server-side, so without a
     * cap a failing slot would be clicked forever.
     */
    public static final int MAX_ATTEMPTS_PER_SLOT = 3;

    /**
     * How often the same locked slot may have its contents thrown on the ground in one connection.
     *
     * <p>A throw is not a terminal state: vanilla only sets a two second pickup delay, so a player
     * standing where the stack landed picks it straight back up, the server puts it back in the
     * locked slot and the evictor throws it again. {@link #MAX_ATTEMPTS_PER_SLOT} cannot bound that
     * because it keys on the slot's content, which genuinely changes every cycle. After this many
     * drops the slot falls back to {@link EvictionAction#NONE} for the rest of the connection.
     */
    public static final int MAX_DROPS_PER_SLOT = 3;

    /**
     * How long the same stack must stay un-dropped from the same slot after a throw, in client
     * ticks (60 seconds). Long enough that a player who walks away never notices it and a player
     * who stands still is not caught in a drop/pickup loop every two seconds.
     */
    public static final int DROP_REPEAT_COOLDOWN_TICKS = 1200;

    private EvictionPolicy() {
    }

    /**
     * Returns whether an inventory click may be sent on this tick under the movement gate.
     *
     * <p>This is a movement gate, not a rate limit: GrimAC's {@code MultiActionsC} check cancels
     * any container click that arrives while the player is sprinting, sneaking or holding a
     * movement key, no matter how rarely it is sent. Queueing until the player stands still is the
     * only thing that helps.
     *
     * @param sprinting whether the player is sprinting.
     * @param sneaking whether the player is sneaking.
     * @param movementInput whether any movement key (including jump) is held.
     * @return true when a click is safe to send.
     */
    public static boolean movementGateOpen(boolean sprinting, boolean sneaking, boolean movementInput) {
        return !sprinting && !sneaking && !movementInput;
    }

    /**
     * Returns whether an inventory click may be sent on this tick, honouring the player's
     * {@code evictImmediately} setting.
     *
     * <p>The movement gate is <b>not</b> unconditional: it is a default the player may switch off.
     * With {@code evictImmediately} on, clicks go out on the first tick the other preconditions
     * hold (presence ABSENT, no screen open, the player's own inventory menu is the target, budgets
     * not exhausted) - faster, at the cost of the clicks being silently cancelled on a server whose
     * anti-cheat rejects container clicks from a moving player, which shows up as a failed eviction
     * or an item setback. With it off, the gate applies as before. Nothing else changes either way.
     *
     * @param evictImmediately whether the player opted out of the movement gate.
     * @param sprinting whether the player is sprinting.
     * @param sneaking whether the player is sneaking.
     * @param movementInput whether any movement key (including jump) is held.
     * @return true when a click may be sent.
     */
    public static boolean clickGateOpen(boolean evictImmediately, boolean sprinting, boolean sneaking,
            boolean movementInput) {
        return evictImmediately || movementGateOpen(sprinting, sneaking, movementInput);
    }

    /**
     * Picks the action for one occupied locked slot.
     *
     * @param mode the configured fallback mode; null is treated as {@link FallbackMode#OFF}.
     * @param canRelocate whether a quick-move would actually find a destination.
     * @return the action to perform, never null.
     */
    public static EvictionAction decide(FallbackMode mode, boolean canRelocate) {
        if (mode == null || mode == FallbackMode.OFF) {
            return EvictionAction.NONE;
        }
        if (canRelocate) {
            return EvictionAction.MOVE;
        }
        return mode == FallbackMode.MOVE_OR_DROP ? EvictionAction.DROP : EvictionAction.NONE;
    }
}
