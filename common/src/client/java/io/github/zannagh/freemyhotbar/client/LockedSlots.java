package io.github.zannagh.freemyhotbar.client;

import io.github.zannagh.freemyhotbar.mixin.client.CreativeSlotWrapperAccessor;
import io.github.zannagh.freemyhotbar.slot.GuiClick;
import io.github.zannagh.freemyhotbar.slot.GuiInteraction;
import io.github.zannagh.freemyhotbar.slot.GuiInteractionPolicy;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/**
 * Client-side bridge between a live container screen and the MC-free lock model: resolves whether
 * a {@link Slot} is one of the local player's locked hotbar slots, and describes a slot
 * interaction as a {@link GuiClick} for the policy to judge.
 *
 * <p>Hotbar slots are resolved structurally — the slot's container must be the local player's
 * {@code Inventory} and its container index must fall in {@code [0, 9)} — rather than by a
 * hardcoded menu index. {@code InventoryMenu} happens to place the hotbar at menu slots 36-44, but
 * every other menu (chests, crafting tables, the creative item tabs) lays the player's inventory
 * out at its own offsets, and this resolution works in all of them.
 *
 * <p>One vanilla slot lies about its container index and has to be unwrapped first: the creative
 * screen's Inventory tab replaces its slots with {@code CreativeModeInventoryScreen$SlotWrapper},
 * which passes the wrapped menu's slot index (0-45) to {@code Slot}'s constructor while keeping the
 * wrapped slot's container. A wrapped hotbar slot therefore reports container index 36-44 (missed
 * entirely) and a wrapped armor slot reports 5-8 (wrongly read as hotbar slots 5-8, blocking
 * armour placement). {@link CreativeSlotWrapperAccessor} exposes the wrapped slot so both cases
 * resolve correctly.
 */
public final class LockedSlots {

    private LockedSlots() {
    }

    /**
     * Returns whether the player asked for their own clicks into locked slots to be suppressed.
     *
     * @return the current value of the {@code blockGuiInteractions} setting.
     */
    public static boolean guardActive() {
        return FreeMyHotbarClient.config().blockGuiInteractions();
    }

    /**
     * Returns the local player's hotbar index a slot maps to.
     *
     * @param slot the slot to resolve; may be null.
     * @return the hotbar index 0-8, or -1 when the slot is not one of the player's hotbar slots.
     */
    public static int hotbarIndex(Slot slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        Slot resolved = unwrap(slot);
        if (resolved == null || player == null || resolved.container != player.getInventory()) {
            return -1;
        }
        int index = resolved.getContainerSlot();
        if (index < 0 || index >= SlotBlock.HOTBAR_SLOT_COUNT) {
            return -1;
        }
        return index;
    }

    /**
     * Returns whether a hotbar index is currently locked.
     *
     * @param hotbarIndex the index to test; out-of-range values are never locked.
     * @return true when the index is a locked hotbar slot.
     */
    public static boolean isLockedIndex(int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex >= SlotBlock.HOTBAR_SLOT_COUNT) {
            return false;
        }
        return FreeMyHotbarClient.config().isBlocked(hotbarIndex);
    }

    /**
     * Returns whether a slot is one of the local player's locked hotbar slots.
     *
     * @param slot the slot to test; may be null.
     * @return true when the slot is locked.
     */
    public static boolean isLockedSlot(Slot slot) {
        return isLockedIndex(hotbarIndex(slot));
    }

    /**
     * Describes one slot interaction for {@code GuiInteractionPolicy}.
     *
     * @param slot the slot under the cursor; may be null (a click outside the window).
     * @param button the vanilla button argument — for {@link ClickType#SWAP} either the hotbar
     *     index the pressed number key names, or
     *     {@link GuiInteractionPolicy#OFFHAND_SWAP_BUTTON} for the offhand swap key (F), which
     *     vanilla dispatches through the very same {@code slotClicked} call.
     * @param type the vanilla click type.
     * @return the described click, or null when there is no player or the type is unknown.
     */
    public static GuiClick describe(Slot slot, int button, ClickType type) {
        GuiInteraction kind = kindOf(type);
        LocalPlayer player = Minecraft.getInstance().player;
        if (kind == null || player == null) {
            return null;
        }
        boolean swap = kind == GuiInteraction.SWAP;
        int source = swap ? GuiInteractionPolicy.swapSourceSlot(button) : -1;
        boolean swapSourceHasItem = source >= 0 && !player.getInventory().getItem(source).isEmpty();
        return new GuiClick(
                kind,
                isLockedSlot(slot),
                slot != null && slot.hasItem(),
                !player.containerMenu.getCarried().isEmpty(),
                isLockedIndex(source),
                swapSourceHasItem);
    }

    /** Resolves a creative Inventory-tab slot wrapper to the real menu slot it stands for. */
    private static Slot unwrap(Slot slot) {
        if (slot instanceof CreativeSlotWrapperAccessor wrapper) {
            return wrapper.freeMyHotbar$getTarget();
        }
        return slot;
    }

    private static GuiInteraction kindOf(ClickType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PICKUP -> GuiInteraction.PICKUP;
            case QUICK_MOVE -> GuiInteraction.QUICK_MOVE;
            case SWAP -> GuiInteraction.SWAP;
            case CLONE -> GuiInteraction.CLONE;
            case THROW -> GuiInteraction.THROW;
            case QUICK_CRAFT -> GuiInteraction.QUICK_CRAFT;
            case PICKUP_ALL -> GuiInteraction.PICKUP_ALL;
        };
    }
}
