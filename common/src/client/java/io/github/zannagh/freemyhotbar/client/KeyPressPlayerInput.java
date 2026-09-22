//? if >= 1.21.2 {
/*package io.github.zannagh.freemyhotbar.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

/^*
 * {@link PlayerInputAccess} for the game versions that hold the movement keys in the
 * {@code world.entity.player.Input} RECORD, reached through {@code ClientInput.keyPresses}.
 *
 * <p>Asking through the record is what collapses two boundaries into this one class: 1.21.2
 * moved the booleans there while keeping the float impulses beside them, and 1.21.5 then deleted
 * those impulses. The record itself is unchanged across both, so nothing here has to know which
 * of the two is running.
 *
 * <p>Unlike the impulse reader this sees the raw key states, so a pair of opposite keys reads as
 * movement rather than as standing still. That is the only behavioural difference between the two
 * implementations, and it is the one the game itself makes from 1.21.5 on, where the impulses the
 * older reader preferred no longer exist.
 *
 * @param player the local player whose input is read.
 ^/
record KeyPressPlayerInput(LocalPlayer player) implements PlayerInputAccess {

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
        if (player.input == null) {
            return false;
        }
        Input keys = player.input.keyPresses;
        if (keys == null) {
            return false;
        }
        return keys.forward() || keys.backward() || keys.left() || keys.right()
                || keys.jump() || keys.shift();
    }
}
*///?}
