package io.github.zannagh.freemyhotbar.client;

import net.minecraft.client.player.LocalPlayer;

/**
 * Reads the local player's movement state for the eviction click gate.
 *
 * <p>The one place in the mod where a real interface earns its keep. The three questions below are
 * stable, but where their answers live is not: up to 1.21.1 the movement keys are float impulses
 * plus booleans on {@code Input}; 1.21.2 renamed that class to {@code ClientInput} and moved the
 * booleans into an {@code Input} record reached through {@code keyPresses}; 1.21.5 then dropped
 * the float impulses from {@code ClientInput} entirely. Asking through {@code keyPresses} from
 * 1.21.2 on collapses those last two changes into a single implementation.
 *
 * <p>Callers only ever go through {@link #of}, so adding that implementation is a change to this
 * file and a new class beside it - never to the eviction logic.
 */
public interface PlayerInputAccess {

    /**
     * Returns the reader for the running game version.
     *
     * @param player the local player.
     * @return a reader over that player's current movement state.
     */
    static PlayerInputAccess of(LocalPlayer player) {
        //? if >= 1.21.2 {
        /*return new KeyPressPlayerInput(player);
        *///?} else {
        return new ImpulsePlayerInput(player);
        //?}
    }

    /**
     * Returns whether the player is sprinting.
     *
     * @return true while sprinting.
     */
    boolean sprinting();

    /**
     * Returns whether the player is sneaking.
     *
     * @return true while sneaking.
     */
    boolean sneaking();

    /**
     * Returns whether any movement key - including jump - is held.
     *
     * @return true while the player is asking to move.
     */
    boolean hasMovementInput();
}
