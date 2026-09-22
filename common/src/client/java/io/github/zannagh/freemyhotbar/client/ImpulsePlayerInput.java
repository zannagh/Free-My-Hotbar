//? if < 1.21.2 {
package io.github.zannagh.freemyhotbar.client;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;

/**
 * {@link PlayerInputAccess} for the game versions whose {@code Input} still carries the movement
 * keys directly: the two float impulses plus the jump and sneak booleans.
 *
 * <p>The impulses rather than the raw key states are what the gate wants - they are already the
 * resolved "is the player asking to move" answer, so a pair of opposite keys reads as standing
 * still, exactly as the player experiences it.
 *
 * @param player the local player whose input is read.
 */
record ImpulsePlayerInput(LocalPlayer player) implements PlayerInputAccess {

    @Override
    public boolean sprinting() {
        return player.isSprinting();
    }

    @Override
    public boolean sneaking() {
        return player.isShiftKeyDown();
    }

    @Override
    public boolean hasMovementInput() {
        Input input = player.input;
        if (input == null) {
            return false;
        }
        return input.forwardImpulse != 0.0F
                || input.leftImpulse != 0.0F
                || input.jumping
                || input.shiftKeyDown;
    }
}
//?}
