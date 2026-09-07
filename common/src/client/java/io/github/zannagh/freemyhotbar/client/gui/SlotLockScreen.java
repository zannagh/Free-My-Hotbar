package io.github.zannagh.freemyhotbar.client.gui;

import io.github.zannagh.freemyhotbar.client.config.ClientSlotConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SlotLockScreen extends Screen {

    private static final int SLOT_COUNT = 9;
    private static final int GRID_COLUMNS = 3;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

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

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, BUTTON_HEIGHT).build());
    }

    private Component labelFor(int slot) {
        int display = slot + 1;
        if (config.isLocked(slot)) {
            return Component.translatable("screen.free-my-hotbar.slot.locked", display);
        }
        return Component.translatable("screen.free-my-hotbar.slot.free", display);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(font,
                Component.translatable("screen.free-my-hotbar.help"), width / 2, 40, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
