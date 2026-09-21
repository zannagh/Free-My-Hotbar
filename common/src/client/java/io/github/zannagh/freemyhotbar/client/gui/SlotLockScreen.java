package io.github.zannagh.freemyhotbar.client.gui;

import java.util.List;

import io.github.zannagh.freemyhotbar.client.ServerModPresence;
import io.github.zannagh.freemyhotbar.client.config.ClientSlotConfig;
import io.github.zannagh.freemyhotbar.config.FallbackMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class SlotLockScreen extends Screen {

    private static final int SLOT_COUNT = 9;
    private static final int GRID_COLUMNS = 3;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int OPTION_WIDTH = 248;
    private static final int GAP = 4;
    private static final int MODE_COLOR = 0xA0A0A0;

    /** Number of stacked full-width option buttons under the slot grid. */
    private static final int OPTION_ROWS = 3;

    /** Y offset of the Done button from the bottom edge, and the height it reserves. */
    private static final int DONE_OFFSET = 27;

    /** First line of the presence/mode text. */
    private static final int MODE_TEXT_Y = 52;

    private final ClientSlotConfig config;

    public SlotLockScreen(ClientSlotConfig config) {
        super(Component.translatable("screen.free-my-hotbar.title"));
        this.config = config;
    }

    @Override
    protected void init() {
        int gridWidth = GRID_COLUMNS * BUTTON_WIDTH + (GRID_COLUMNS - 1) * GAP;
        int rows = SLOT_COUNT / GRID_COLUMNS;
        int startX = (width - gridWidth) / 2;
        int startY = height / 2 - (rows * (BUTTON_HEIGHT + GAP)) / 2;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int column = slot % GRID_COLUMNS;
            int row = slot / GRID_COLUMNS;
            int x = startX + column * (BUTTON_WIDTH + GAP);
            int y = startY + row * (BUTTON_HEIGHT + GAP);
            int captured = slot;
            Button button = Button.builder(labelFor(captured), b -> {
                config.toggle(captured);
                b.setMessage(labelFor(captured));
            }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            addRenderableWidget(button);
        }

        addOptionButtons(startY + rows * (BUTTON_HEIGHT + GAP) + GAP);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - DONE_OFFSET, 200, BUTTON_HEIGHT).build());
    }

    /**
     * Adds the fallback-mode cycle button and the GUI-interaction toggle, stacked from {@code y}.
     *
     * <p>Both are clamped to the window: a narrow screen shrinks them rather than letting them run
     * off the sides, and a short one lifts them so the pair never lands on top of the Done button.
     */
    private void addOptionButtons(int y) {
        int optionWidth = Math.min(OPTION_WIDTH, Math.max(BUTTON_WIDTH, width - 2 * GAP));
        int x = (width - optionWidth) / 2;
        int step = BUTTON_HEIGHT + GAP;
        int stackHeight = OPTION_ROWS * BUTTON_HEIGHT + (OPTION_ROWS - 1) * GAP;
        int highestY = height - DONE_OFFSET - GAP - stackHeight;
        int topY = Math.max(0, Math.min(y, highestY));

        addRenderableWidget(Button.builder(fallbackLabel(), b -> {
            config.setFallbackMode(config.fallbackMode().next());
            b.setMessage(fallbackLabel());
        }).bounds(x, topY, optionWidth, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(blockGuiLabel(), b -> {
            config.setBlockGuiInteractions(!config.blockGuiInteractions());
            b.setMessage(blockGuiLabel());
        }).bounds(x, topY + step, optionWidth, BUTTON_HEIGHT).build());

        Button evictButton = Button.builder(evictImmediatelyLabel(), b -> {
            config.setEvictImmediately(!config.evictImmediately());
            b.setMessage(evictImmediatelyLabel());
        }).bounds(x, topY + 2 * step, optionWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(
                        Component.translatable("screen.free-my-hotbar.evict_immediately.hint")))
                .build();
        addRenderableWidget(evictButton);
    }

    private Component labelFor(int slot) {
        int display = slot + 1;
        if (config.isBlocked(slot)) {
            return Component.translatable("screen.free-my-hotbar.slot.locked", display);
        }
        return Component.translatable("screen.free-my-hotbar.slot.free", display);
    }

    private Component fallbackLabel() {
        FallbackMode mode = config.fallbackMode();
        return Component.translatable("screen.free-my-hotbar.fallback",
                Component.translatable(mode.translationKey()));
    }

    private Component evictImmediatelyLabel() {
        return Component.translatable("screen.free-my-hotbar.evict_immediately",
                CommonComponents.optionStatus(config.evictImmediately()));
    }

    private Component blockGuiLabel() {
        return Component.translatable("screen.free-my-hotbar.block_gui",
                CommonComponents.optionStatus(config.blockGuiInteractions()));
    }

    /** The honest one-line description of how locked slots are enforced on this connection. */
    private static Component modeLine() {
        switch (ServerModPresence.state()) {
            case PRESENT:
                return Component.translatable("screen.free-my-hotbar.mode.present");
            case ABSENT:
                return Component.translatable("screen.free-my-hotbar.mode.absent");
            default:
                return Component.translatable("screen.free-my-hotbar.mode.unknown");
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(font,
                Component.translatable("screen.free-my-hotbar.help"), width / 2, 40, MODE_COLOR);
        // The mode sentences are long; wrap them or they run off the sides of a narrow window.
        List<FormattedCharSequence> lines = font.split(modeLine(), Math.max(1, width - 2 * GAP));
        int y = MODE_TEXT_Y;
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawCenteredString(font, line, width / 2, y, MODE_COLOR);
            y += font.lineHeight;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
