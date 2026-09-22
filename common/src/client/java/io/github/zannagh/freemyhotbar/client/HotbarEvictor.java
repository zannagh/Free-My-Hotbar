package io.github.zannagh.freemyhotbar.client;

import java.util.Arrays;

import io.github.zannagh.freemyhotbar.config.FallbackMode;
import io.github.zannagh.freemyhotbar.fallback.EvictionAction;
import io.github.zannagh.freemyhotbar.fallback.EvictionPolicy;
import io.github.zannagh.freemyhotbar.fallback.EvictionTracker;
import io.github.zannagh.freemyhotbar.fallback.LockedSlotBaseline;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only fallback for servers without the mod: items still get picked up into locked hotbar
 * slots, so they are moved out again right afterwards (work item B), optionally thrown on the
 * ground when the inventory has no room left (work item C).
 *
 * <p>Three things shape this:
 * <ul>
 *   <li><b>It only reacts to ARRIVALS, never to contents.</b> {@link LockedSlotBaseline} keeps a
 *       rolling per-slot snapshot of the hotbar and only what grew against it is evicted, so the
 *       sword in slot 0 survives a pickup into slot 5. That also closes the shift-click hole for
 *       free: a quick-move in another container whose destination the server picks to be a locked
 *       slot sends no take-item packet, but it does grow the slot. An arrival the player's own
 *       click asked for is adopted instead of evicted - see {@link #notePlayerPlacement(int)} -
 *       and eviction stays suppressed while any screen is open.
 *   <li><b>Anti-cheat is about movement, not rate.</b> GrimAC cancels container clicks sent while
 *       the player sprints, sneaks or holds a movement key, so by default clicks are queued and
 *       flushed only on a tick where the player stands still. The player can opt out of that with
 *       the {@code evictImmediately} setting. See {@link EvictionPolicy#clickGateOpen}.
 *   <li><b>A failed quick-move is silent.</b> Destinations are predicted up-front by
 *       {@link EvictionTargets} and attempts are capped per slot, so a full inventory cannot turn
 *       into an endless click loop.
 * </ul>
 *
 * <p>Armor, elytra, mob heads, carved pumpkins and shields are never quick-moved at all while
 * their equipment slot is free, because vanilla would EQUIP them - see
 * {@link EvictionTargets#canRelocate}.
 *
 * <p>In {@link FallbackMode#MOVE_OR_DROP} a throw is not a terminal state. The stack keeps its
 * despawn timer, is lootable by other players, and becomes re-pickable after the vanilla two
 * second pickup delay: a player standing where it landed picks it straight back up, the server
 * puts it back in the locked slot and the evictor would throw it again - a loop with no end that
 * leaves the stack on the ground every two seconds. Drops are therefore bounded twice over by
 * {@link EvictionTracker#mayDrop} (a long per-signature cooldown plus a hard per-connection cap),
 * and the player is told once per connection when a slot stops being drained.
 */
public final class HotbarEvictor {

    /** {@code InventoryMenu} slot index of hotbar slot 0; 36-44 hotbar, 9-35 main, 45 offhand. */
    private static final int MENU_HOTBAR_START = 36;

    /** Container id of the player's own inventory menu, which is always open. */
    private static final int INVENTORY_CONTAINER_ID = 0;

    /** Button 1 on a {@link ClickType#THROW} click drops the whole stack rather than one item. */
    private static final int THROW_WHOLE_STACK = 1;

    private static final EvictionTracker TRACKER = new EvictionTracker(SlotBlock.HOTBAR_SLOT_COUNT);

    private static final LockedSlotBaseline BASELINE =
            new LockedSlotBaseline(SlotBlock.HOTBAR_SLOT_COUNT);

    /** Which locked slots currently hold contents that arrived without the player asking. */
    private static final boolean[] ARRIVALS = new boolean[SlotBlock.HOTBAR_SLOT_COUNT];

    /** One chat notice per connection when a slot's drop budget runs out. */
    private static boolean dropLimitNoticeSent;

    private HotbarEvictor() {
    }

    /**
     * Arms the evictor when the local player is the one who picked an item up.
     *
     * <p>A hint, no longer the trigger: what gets evicted is decided by the baseline diff in
     * {@link #scan}, which sees the arrival whether or not a take-item packet announced it. The
     * packet still opens the window a tick earlier than the scan would.
     *
     * @param collectorEntityId the entity id from the take-item packet.
     */
    public static void onItemPickedUp(int collectorEntityId) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (player != null && player.getId() == collectorEntityId) {
            TRACKER.notePickup();
        }
    }

    /**
     * Records that the player's own click named a locked hotbar slot as its destination, so what
     * lands there is adopted into the baseline rather than evicted straight back out.
     *
     * <p>Only for the clicks {@code blockGuiInteractions} governs and only when that setting is
     * OFF: with it on the click never happens at all. A shift-click elsewhere in the menu is NOT
     * such a click - the player names a source there, never a destination.
     *
     * @param slot the hotbar slot index; out-of-range values are ignored.
     */
    public static void notePlayerPlacement(int slot) {
        BASELINE.notePlacement(slot);
    }

    /** Drops all queued state; call on disconnect and whenever the player changes the config. */
    public static void reset() {
        TRACKER.reset();
        BASELINE.reset();
        Arrays.fill(ARRIVALS, false);
        dropLimitNoticeSent = false;
    }

    /**
     * Runs one client tick of the evictor: refreshes the hotbar baseline, arms on anything that
     * arrived in a locked slot unasked, and flushes the eviction clicks when the gate is open.
     *
     * <p>The baseline is refreshed even when the fallback cannot act at all (server has the mod,
     * fallback off, nothing locked). Skipping it there would leave a stale snapshot behind, and the
     * moment the player locked a slot or joined a modless server the whole hotbar would read as a
     * pile of fresh arrivals and be swept out from under them.
     *
     * @param minecraft the client instance; null is ignored.
     */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            TRACKER.reset();
            BASELINE.reset();
            Arrays.fill(ARRIVALS, false);
            return;
        }
        // Decay first and unconditionally: the pickup mixin arms the tracker whatever the config
        // says, so skipping this while the fallback is off would let a ten-minute-old pickup fire a
        // sweep the instant the player locks their first slot.
        TRACKER.tick();
        FallbackMode mode = FreeMyHotbarClient.config().fallbackMode();
        boolean armed = ServerModPresence.state() == ServerModPresence.State.ABSENT
                && mode != FallbackMode.OFF
                && FreeMyHotbarClient.config().hasBlockedSlots();
        boolean gateOpen = canClick(minecraft, player);
        boolean anyArrival = scan(player, armed, gateOpen);
        if (!anyArrival) {
            TRACKER.clearPending();
            return;
        }
        TRACKER.notePickup();
        if (!TRACKER.ready() || !gateOpen) {
            return;
        }
        flush(minecraft, player, mode);
    }

    /**
     * Compares every hotbar slot against the baseline, records which locked ones hold an
     * unexplained arrival and re-baselines the rest.
     *
     * @param player the local player.
     * @param armed whether the fallback may act at all on this connection.
     * @param gateOpen whether an eviction click could be sent this tick; the pursuit budget of an
     *     outstanding arrival only runs down on such ticks, so a player who is browsing a chest or
     *     sprinting never loses an arrival without a single attempt having been made.
     * @return true when at least one locked slot holds an unexplained arrival.
     */
    private static boolean scan(LocalPlayer player, boolean armed, boolean gateOpen) {
        Inventory inventory = player.getInventory();
        boolean any = false;
        for (int slot = 0; slot < SlotBlock.HOTBAR_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            int count = stack.isEmpty() ? 0 : stack.getCount();
            int itemId = Item.getId(stack.getItem());
            boolean arrival = false;
            if (armed && FreeMyHotbarClient.config().isBlocked(slot)) {
                arrival = BASELINE.update(slot, itemId, count, gateOpen);
            } else {
                BASELINE.accept(slot, itemId, count);
            }
            if (count == 0) {
                TRACKER.noteEmpty(slot);
            }
            ARRIVALS[slot] = arrival;
            any |= arrival;
        }
        return any;
    }

    /** Whether a container click is both meaningful and safe to send on this tick. */
    private static boolean canClick(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.gameMode == null || ClientScreens.anyOpen(minecraft)) {
            return false;
        }
        // Slot indices below are InventoryMenu's; never click while some other menu is the target.
        if (player.containerMenu != player.inventoryMenu) {
            return false;
        }
        PlayerInputAccess input = PlayerInputAccess.of(player);
        return EvictionPolicy.clickGateOpen(
                FreeMyHotbarClient.config().evictImmediately(),
                input.sprinting(),
                input.sneaking(),
                input.hasMovementInput());
    }

    private static void flush(Minecraft minecraft, LocalPlayer player, FallbackMode mode) {
        TRACKER.noteFlush();
        // One sweep per flush. A second sweep in the same tick would only re-read this sweep's own
        // local prediction - no packet is processed in between - so it can never see an item that
        // landed in a just-freed slot. The flush cooldown is what covers that: the next pass runs
        // after the server's slot broadcast has actually arrived.
        sweep(minecraft, player, mode);
    }

    /**
     * Acts on the arrivals {@link #scan} found. Slots the player's own tools sit in are not in
     * {@link #ARRIVALS} and are therefore never touched, however full the rest of the hotbar is.
     */
    private static void sweep(Minecraft minecraft, LocalPlayer player, FallbackMode mode) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < SlotBlock.HOTBAR_SLOT_COUNT; slot++) {
            if (!ARRIVALS[slot]) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int signature = signature(stack);
            EvictionAction action = EvictionPolicy.decide(mode, EvictionTargets.canRelocate(player, stack));
            if (action == EvictionAction.DROP && !TRACKER.mayDrop(slot, signature)) {
                noticeDropLimit(player, slot);
                continue;
            }
            if (action == EvictionAction.NONE || !TRACKER.mayAttempt(slot, signature)) {
                continue;
            }
            TRACKER.recordAttempt(slot);
            if (action == EvictionAction.DROP) {
                TRACKER.recordDrop(slot, signature);
            }
            click(minecraft, player, slot, action);
        }
    }

    /**
     * Says once per connection that the mod has stopped throwing a slot's contents away. Only for
     * the hard per-connection cap: the per-signature cooldown is temporary and self-healing, so
     * announcing it would be noise.
     */
    private static void noticeDropLimit(LocalPlayer player, int slot) {
        if (dropLimitNoticeSent || !TRACKER.dropsExhausted(slot)) {
            return;
        }
        dropLimitNoticeSent = true;
        // See ServerModNotice: 26.1 split the chat/action-bar flag into two methods.
        //? if >= 26.1 {
        /*player.sendSystemMessage(Component.translatable("chat.free-my-hotbar.drop_limit", slot + 1));
        *///?} else {
        player.displayClientMessage(
                Component.translatable("chat.free-my-hotbar.drop_limit", slot + 1), false);
        //?}
    }

    /**
     * Sends the relocation click. {@code handleInventoryMouseClick} needs no open screen - it only
     * uses {@code player.containerMenu} - and it is what keeps the container state id and the
     * changed-slot diff in sync, which a hand-rolled packet would not.
     */
    private static void click(Minecraft minecraft, LocalPlayer player, int slot, EvictionAction action) {
        boolean drop = action == EvictionAction.DROP;
        minecraft.gameMode.handleInventoryMouseClick(
                INVENTORY_CONTAINER_ID,
                MENU_HOTBAR_START + slot,
                drop ? THROW_WHOLE_STACK : 0,
                drop ? ClickType.THROW : ClickType.QUICK_MOVE,
                player);
    }

    /** Identifies a slot's content so the retry budget resets as soon as the stack changes. */
    private static int signature(ItemStack stack) {
        return Item.getId(stack.getItem()) * 31 + stack.getCount();
    }
}
