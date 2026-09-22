package io.github.zannagh.freemyhotbar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The two screen questions the mod asks the client, kept in the one place that has to know where
 * the answer lives.
 *
 * <p>26.2 moved the open screen off {@code Minecraft} and onto its {@code Gui}: the public
 * {@code screen} field and {@code setScreen} are gone, and {@code gui.screen()} /
 * {@code gui.setScreen(...)} took their place. A moved receiver is not a rename, so the global
 * replacement table cannot carry it - but it is only these two expressions, and holding them here
 * leaves every caller invariant.
 */
public final class ClientScreens {

    private ClientScreens() {
    }

    /**
     * Opens a screen on the client.
     *
     * @param minecraft the client instance.
     * @param screen the screen to show.
     */
    public static void open(Minecraft minecraft, Screen screen) {
        //? if >= 26.2 {
        /*minecraft.gui.setScreen(screen);
        *///?} else {
        minecraft.setScreen(screen);
        //?}
    }

    /**
     * Returns the screen currently taking the player's input.
     *
     * @param minecraft the client instance.
     * @return the open screen, or null when the player is in the world.
     */
    public static @Nullable Screen current(Minecraft minecraft) {
        //? if >= 26.2 {
        /*return minecraft.gui.screen();
        *///?} else {
        return minecraft.screen;
        //?}
    }

    /**
     * Returns whether any screen is currently open.
     *
     * @param minecraft the client instance.
     * @return true while a screen is taking the player's input.
     */
    public static boolean anyOpen(Minecraft minecraft) {
        return current(minecraft) != null;
    }
}
