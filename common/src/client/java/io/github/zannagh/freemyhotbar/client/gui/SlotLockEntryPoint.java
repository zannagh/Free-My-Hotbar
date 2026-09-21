package io.github.zannagh.freemyhotbar.client.gui;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.zannagh.eunomia.client.EunomiaClient;
import de.zannagh.eunomia.client.gui.screens.EunomiaSettingsEntryPoint;
import de.zannagh.eunomia.client.ui.ScreenAccessor;
import de.zannagh.eunomia.client.ui.ScreenInitializationManager;
import de.zannagh.eunomia.client.ui.ScreenInitializer;
import de.zannagh.eunomia.client.ui.ScreenWidgetSink;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Puts a button that opens {@link SlotLockScreen} where eunomia would otherwise have put its own
 * settings button, and moves eunomia's button onto the lock screen in exchange.
 *
 * <p>Installing eunomia adds an "Eunomia Settings" entry to vanilla's online options screen. Two
 * mod buttons in the same top-left corner would overlap, and the entry a Free My Hotbar player is
 * looking for on that screen is the lock screen, not the sync settings — so the vanilla screen gets
 * ours and eunomia's moves one level in, where it is still reachable for a player who does want it.
 *
 * <p>The target is read from {@link EunomiaSettingsEntryPoint#targetScreen()} rather than naming a
 * vanilla class: the online options screen has changed package across the supported versions, and
 * eunomia already resolves it per variant. Read it before retargeting, or it reports the lock screen.
 *
 * <p>This is an additional entry point. The {@code H} keybind (see {@code FreeMyHotbarKeys}) still
 * opens the same screen and is untouched by any of this.
 */
public final class SlotLockEntryPoint implements ScreenInitializer {

    /** Same corner and metrics eunomia's own button used, so the screen looks unchanged. */
    private static final int MARGIN = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_BUTTON_WIDTH = 120;
    private static final int MAX_BUTTON_WIDTH = 140;
    private static final int LABEL_PADDING = 16;

    private static final SlotLockEntryPoint INSTANCE = new SlotLockEntryPoint();

    private static final Object LOCK = new Object();

    private static boolean installed;

    /**
     * Screens that were handed a button, so a resize (which re-runs init with the widget lists
     * intact) does not collect a second one while a rebuild (which clears them) still gets one back.
     * Weak-keyed so a closed screen does not keep itself alive.
     */
    private final Map<Screen, AbstractWidget> attached = Collections.synchronizedMap(new WeakHashMap<>());

    private SlotLockEntryPoint() {
    }

    /**
     * Installs the entry button and moves eunomia's own button to the lock screen. Idempotent, and
     * safe to call before or after eunomia's client init: eunomia reconciles its own registration.
     */
    public static void install() {
        synchronized (LOCK) {
            if (installed) {
                return;
            }
            installed = true;
            Class<? extends Screen> optionsScreen = EunomiaSettingsEntryPoint.targetScreen();
            ScreenInitializationManager.INSTANCE.registerInitializer(optionsScreen, INSTANCE);
            EunomiaClient.configure()
                    .settingsButton(SlotLockScreen.class)
                    .apply();
        }
    }

    @Override
    public void initialize(Screen screen, ScreenWidgetSink sink) {
        if (isStillAttached(screen)) {
            return;
        }
        Component label = Component.translatable("screen.free-my-hotbar.title");
        int width = Math.min(MAX_BUTTON_WIDTH,
                Math.max(MIN_BUTTON_WIDTH, Minecraft.getInstance().font.width(label) + LABEL_PADDING));
        Button button = Button.builder(label, press -> open())
                .bounds(MARGIN, MARGIN, width, BUTTON_HEIGHT)
                .build();
        button.setTooltip(Tooltip.create(
                Component.translatable("screen.free-my-hotbar.help")));
        sink.eunomia$addWidget(button);
        attached.put(screen, button);
    }

    private boolean isStillAttached(Screen screen) {
        AbstractWidget previous = attached.get(screen);
        if (previous == null) {
            return false;
        }
        List<Renderable> renderables = ((ScreenAccessor) screen).eunomia$getRenderables();
        return renderables.contains(previous);
    }

    private static void open() {
        Minecraft.getInstance().setScreen(new SlotLockScreen(FreeMyHotbarClient.config()));
    }
}
