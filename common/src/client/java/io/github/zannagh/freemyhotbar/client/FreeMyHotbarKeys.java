package io.github.zannagh.freemyhotbar.client;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.zannagh.freemyhotbar.client.gui.SlotLockScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Static holder for the mod's client {@link KeyMapping}. The binding is exposed here but registered
 * per-loader (Fabric/Forge glue) rather than in common code.
 */
public final class FreeMyHotbarKeys {

    /** Translation key for the keybind name. */
    public static final String KEY_NAME = "key.free-my-hotbar.open_screen";

    /** Category translation key for the keybind. */
    public static final String KEY_CATEGORY = "key.categories.misc";

    /** The keybind opening the slot-lock screen; default is the H key. */
    public static final KeyMapping OPEN_SCREEN = new KeyMapping(
            KEY_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KEY_CATEGORY);

    private FreeMyHotbarKeys() {
    }

    /** Opens the slot-lock screen for the current client instance. */
    public static void openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new SlotLockScreen(FreeMyHotbarClient.config()));
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
