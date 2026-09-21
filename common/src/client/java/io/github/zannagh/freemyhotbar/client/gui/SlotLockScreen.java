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

    /**
     * Lowest y the widget block is allowed to start at, so it clears the title, the help line and
     * the (wrapping) presence text above it. A soft bound: on a window too short to fit everything,
     * the hard bottom clamp against the Done button wins and the block may creep up into this area.
     */
    private static final int CONTENT_TOP_Y = 72;

    private final ClientSlotConfig config;

    public SlotLockScreen(ClientSlotConfig config) {
        super(Component.translatable("screen.free-my-hotbar.title"));
        this.config = config;
    }

    @Override
    protected void init() {
        int rows = SLOT_COUNT / GRID_COLUMNS;
        int gridHeight = rows * BUTTON_HEIGHT + (rows - 1) * GAP;
        int stackHeight = OPTION_ROWS * BUTTON_HEIGHT + (OPTION_ROWS - 1) * GAP;
        int startY = contentTop(gridHeight + GAP + stackHeight);

        addSlotGrid(startY, rows);
        addOptionButtons(startY + gridHeight + GAP);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - DONE_OFFSET, 200, BUTTON_HEIGHT).build());
    }

    /**
     * Places the slot grid and the option stack as ONE block.
     *
     * <p>They have to move together. Clamping only the option stack against the Done button pulled
     * it up over a grid that had been centred independently — at height 240 the grid occupied
     * y=84..152 while the options landed at y=141..209, straight through it. Here the combined
     * block is centred, then pushed down to clear the header text and finally clamped up so its
     * bottom edge stays above the Done button. On a window too short for all of it the bottom clamp
     * wins, because a button hidden behind another button is worse than one crowding a caption.
     *
     * @param contentHeight the combined height of the grid, the gap and the option stack.
     * @return the y the block starts at.
     */
    private int contentTop(int contentHeight) {
        int lowestBottom = height - DONE_OFFSET - GAP;
        int centered = (height - contentHeight) / 2;
        return Math.max(0, Math.min(Math.max(centered, CONTENT_TOP_Y), lowestBottom - contentHeight));
    }

    private void addSlotGrid(int startY, int rows) {
        int gridWidth = GRID_COLUMNS * BUTTON_WIDTH + (GRID_COLUMNS - 1) * GAP;
        int startX = (width - gridWidth) / 2;
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
    }

    /**
     * Adds the fallback-mode cycle button and the GUI-interaction toggles, stacked from {@code y}.
     *
     * <p>Only the WIDTH is clamped here: a narrow window shrinks the buttons rather than letting
     * them run off the sides. The vertical position is settled by {@link #contentTop} for the grid
     * and this stack together, so this method must never move the stack on its own.
     *
     * @param y the top of the option stack, as laid out with the grid.
     */
    private void addOptionButtons(int y) {
        int optionWidth = Math.min(OPTION_WIDTH, Math.max(BUTTON_WIDTH, width - 2 * GAP));
        int x = (width - optionWidth) / 2;
        int step = BUTTON_HEIGHT + GAP;

        addRenderableWidget(Button.builder(fallbackLabel(), b -> {
            config.setFallbackMode(config.fallbackMode().next());
            b.setMessage(fallbackLabel());
        }).bounds(x, y, optionWidth, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(blockGuiLabel(), b -> {
            config.setBlockGuiInteractions(!config.blockGuiInteractions());
            b.setMessage(blockGuiLabel());
        }).bounds(x, y + step, optionWidth, BUTTON_HEIGHT).build());

        Button evictButton = Button.builder(evictImmediatelyLabel(), b -> {
            config.setEvictImmediately(!config.evictImmediately());
            b.setMessage(evictImmediatelyLabel());
        }).bounds(x, y + 2 * step, optionWidth, BUTTON_HEIGHT)
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
