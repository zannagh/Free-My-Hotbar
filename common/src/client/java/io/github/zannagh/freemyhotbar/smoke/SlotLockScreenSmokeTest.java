//? if fcgt {
/*package io.github.zannagh.freemyhotbar.smoke;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.ClientScreens;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
import io.github.zannagh.freemyhotbar.client.config.ClientSlotConfig;
import io.github.zannagh.freemyhotbar.client.gui.SlotLockScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/^*
 * The lock screen end to end: the keybind opens it, pressing a slot button toggles that slot, and
 * the toggle is on disk by the time the press returns.
 *
 * <p>Persistence is asserted by loading a SECOND {@link ClientSlotConfig} off the config file
 * rather than by re-reading the live one, so a toggle that only ever lived in memory fails here.
 *
 * <p>The keybind is rebound onto F24 first. Vanilla's key map has one winner per physical key, so
 * a mod that happens to claim H would swallow the simulated press and this would red as a mod bug
 * rather than as the keybind conflict it is. F24 is a registered key name nothing binds.
 ^/
public final class SlotLockScreenSmokeTest implements FabricClientGameTest {

    /^* The hotbar slot whose button is pressed; button labels are 1-based. ^/
    private static final int TOGGLED_SLOT = 0;

    private static final String FREE_KEY = "key.keyboard.f24";
    private static final int SETTLE_TICKS = 10;

    @Override
    public void runTest(ClientGameTestContext context) {
        FreeMyHotbar.LOGGER.info("{} lock screen starting", SmokeSupport.TAG);
        context.waitForScreen(TitleScreen.class);
        SmokeSupport.Snapshot snapshot = SmokeSupport.Snapshot.capture();
        try (TestSingleplayerContext singleplayer = SmokeSupport.createWorld(context)) {
            try {
                context.runOnClient(client -> SmokeSupport.lockOnly());
                rebindOpenKey(context, InputConstants.getKey(FREE_KEY));
                assertKeybindOpensTheScreen(context);
                assertSlotButtonTogglesAndPersists(context);
            } finally {
                context.setScreen(() -> null);
                rebindOpenKey(context, FreeMyHotbarKeys.OPEN_SCREEN.getDefaultKey());
                context.runOnClient(client -> snapshot.restore());
            }
        }
    }

    private static void rebindOpenKey(ClientGameTestContext context, InputConstants.Key key) {
        context.runOnClient(client -> {
            FreeMyHotbarKeys.OPEN_SCREEN.setKey(key);
            KeyMapping.resetMapping();
        });
    }

    private static void assertKeybindOpensTheScreen(ClientGameTestContext context) {
        // A key press only reaches a KeyMapping while NO screen is taking input, and the world
        // handover can still be showing ReceivingLevelScreen ("downloading terrain") a few ticks
        // after the world context is handed over - on 1.21.8 it reliably is. Pressing then feeds
        // the key to that screen instead and the mapping never sees a click.
        context.waitFor(client -> ClientScreens.current(client) == null);
        context.getInput().pressKey(FreeMyHotbarKeys.OPEN_SCREEN);
        context.waitTicks(SETTLE_TICKS);
        context.runOnClient(client -> {
            Screen screen = ClientScreens.current(client);
            if (!(screen instanceof SlotLockScreen)) {
                SmokeSupport.fail("the open-screen keybind did not open the slot lock screen (got "
                        + (screen == null ? "nothing" : screen.getClass().getName()) + ")");
            }
        });
        SmokeSupport.pass("the open-screen keybind opens the slot lock screen");
    }

    /^* Presses the slot's button, then proves the new state survived a round trip through disk. ^/
    private static void assertSlotButtonTogglesAndPersists(ClientGameTestContext context) {
        pressButton(context, label("screen.free-my-hotbar.slot.free"));
        if (!context.computeOnClient(client -> FreeMyHotbarClient.config().isBlocked(TOGGLED_SLOT))) {
            SmokeSupport.fail("pressing the slot button did not lock hotbar slot " + TOGGLED_SLOT);
        }
        if (!context.computeOnClient(client -> ClientSlotConfig.load().isBlocked(TOGGLED_SLOT))) {
            SmokeSupport.fail("hotbar slot " + TOGGLED_SLOT + " is locked in memory but a config "
                    + "re-read from disk says it is not - the toggle was never saved");
        }
        SmokeSupport.pass("pressing the slot button locks the slot and persists it to the config");

        pressButton(context, label("screen.free-my-hotbar.slot.locked"));
        if (context.computeOnClient(client -> ClientSlotConfig.load().isBlocked(TOGGLED_SLOT))) {
            SmokeSupport.fail("pressing the slot button again did not unlock hotbar slot "
                    + TOGGLED_SLOT);
        }
        SmokeSupport.pass("pressing it again unlocks the slot and persists that too");
    }

    /^* The rendered label of the toggled slot's button, which is how it is found on screen. ^/
    private static String label(String translationKey) {
        return Component.translatable(translationKey, TOGGLED_SLOT + 1).getString();
    }

    /^*
     * Presses the lock screen's own button for the given label, through the widget's own press
     * entry point.
     *
     * <p>Not through {@code TestInput}'s simulated mouse. That works up to 1.21.11 but is inert on
     * the 26.x line: the cursor really does move (its position reads back correctly and the screen
     * stays open), the synthetic button event reaches {@code MouseHandler.onButton}, and nothing
     * happens - the SDL-era input path swallows it. Driving the widget keeps ONE code path on
     * every version, and it is still the screen's own action that runs: the button's handler is
     * what calls {@code ClientSlotConfig#toggle} and re-labels itself.
     *
     * <p>The press method is matched by NAME and arity rather than by signature: 1.21.9 gave it a
     * parameter (the originating input event), which {@code Button} ignores - it just invokes the
     * handler it was built with - so null is a safe argument there.
     ^/
    private static void pressButton(ClientGameTestContext context, String buttonLabel) {
        context.runOnClient(client -> {
            Screen screen = ClientScreens.current(client);
            if (screen == null) {
                SmokeSupport.fail("the lock screen closed before its button could be pressed");
                return;
            }
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && buttonLabel.equals(button.getMessage().getString())) {
                    invokeOnPress(button);
                    return;
                }
            }
            SmokeSupport.fail("no button labelled '" + buttonLabel + "' on the lock screen");
        });
        context.waitTicks(SETTLE_TICKS);
    }

    private static void invokeOnPress(Button button) {
        for (Method candidate : button.getClass().getMethods()) {
            if (!"onPress".equals(candidate.getName()) || candidate.isBridge()
                    || candidate.getParameterCount() > 1) {
                continue;
            }
            try {
                if (candidate.getParameterCount() == 0) {
                    candidate.invoke(button);
                } else {
                    candidate.invoke(button, new Object[] {null});
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(SmokeSupport.TAG + " the button action threw", e);
            }
            return;
        }
        SmokeSupport.fail("Button has no onPress method - the lock screen cannot be driven");
    }
}
*///?}
