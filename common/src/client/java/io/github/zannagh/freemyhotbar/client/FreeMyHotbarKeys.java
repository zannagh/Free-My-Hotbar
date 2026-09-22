package io.github.zannagh.freemyhotbar.client;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.zannagh.freemyhotbar.client.gui.SlotLockScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Static holder for the mod's client {@link KeyMapping}. The binding is exposed here but registered
 * per-loader (Fabric/Forge glue) rather than in common code.
 */
public final class FreeMyHotbarKeys {

    /** Translation key for the keybind name. */
    public static final String KEY_NAME = "key.free-my-hotbar.open_screen";

    /**
     * The controls-screen category the keybind is listed under.
     *
     * <p>Gated rather than replaced: 1.21.9 did not rename the constructor argument, it changed
     * its TYPE - the translation key gave way to a {@code KeyMapping.Category} record holding the
     * same id. Naming the category here keeps the constructor call itself invariant.
     */
    //? if >= 1.21.9 {
    /*public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.MISC;
    *///?} else {
    public static final String KEY_CATEGORY = "key.categories.misc";
    //?}

    /**
     * The keybind opening the slot-lock screen; default is the H key.
     *
     * <p>The key code comes from {@code InputConstants} rather than from LWJGL: the two constants
     * are the same number, but 26.3 dropped LWJGL from the mod compile classpath, while
     * {@code InputConstants.KEY_H} is present unchanged across the whole supported span.
     */
    public static final KeyMapping OPEN_SCREEN = new KeyMapping(
            KEY_NAME, InputConstants.Type.KEYSYM, InputConstants.KEY_H, KEY_CATEGORY);

    private FreeMyHotbarKeys() {
    }

    /** Opens the slot-lock screen for the current client instance. */
    public static void openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientScreens.open(minecraft, new SlotLockScreen(FreeMyHotbarClient.config()));
    }

    /**
     * Edge-triggered per-tick handler: opens the screen once for each queued key press.
     *
     * @param minecraft the client instance.
     */
    public static void handleTick(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        while (OPEN_SCREEN.consumeClick()) {
            openScreen();
        }
    }
}
