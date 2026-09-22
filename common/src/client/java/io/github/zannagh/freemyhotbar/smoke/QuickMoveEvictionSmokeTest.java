//? if fcgt {
/*package io.github.zannagh.freemyhotbar.smoke;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.ServerModPresence;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/^*
 * The shift-click destination case: an item quick-moved out of another part of the menu whose
 * destination the MENU picks - and picks to be a locked hotbar slot.
 *
 * <p>This is the path no GUI hook can reach. The player names a source, never a destination, so
 * there is nothing to refuse at click time; and unlike an auto-pickup it is announced by no
 * take-item packet either. It is caught purely by the baseline diff, which does not care WHY a
 * locked slot grew.
 *
 * <p>Asserted as an A/B against the same click, because "the locked slot is empty afterwards" on
 * its own would also be true of a quick-move that never went there. The control run (fallback
 * disarmed) proves the stack really does land in the locked slot; the armed run then proves it
 * does not stay.
 ^/
public final class QuickMoveEvictionSmokeTest implements FabricClientGameTest {

    /^* The locked hotbar slot the menu will choose: quick-move fills the hotbar from the left. ^/
    private static final int LOCKED_SLOT = 0;

    /^* Source slot of the shift-click. In {@code InventoryMenu} the main inventory starts at 9. ^/
    private static final int SOURCE_MENU_SLOT = 9;
    private static final int SOURCE_INVENTORY_SLOT = 9;

    /^* Container id of the player's own inventory menu, which is always open. ^/
    private static final int INVENTORY_CONTAINER_ID = 0;

    private static final int STACK_SIZE = 16;
    private static final int SETTLE_TICKS = 10;
    private static final int EVICTION_TICKS = 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        FreeMyHotbar.LOGGER.info("{} quick-move destination eviction starting", SmokeSupport.TAG);
        context.waitForScreen(TitleScreen.class);
        SmokeSupport.Snapshot snapshot = SmokeSupport.Snapshot.capture();
        try (TestSingleplayerContext singleplayer = SmokeSupport.createWorld(context)) {
            try {
                context.runOnClient(client -> SmokeSupport.lockOnly(LOCKED_SLOT));
                assertQuickMoveTargetsTheLockedSlot(context, singleplayer);
                assertQuickMoveDestinationIsEvicted(context, singleplayer);
            } finally {
                context.runOnClient(client -> snapshot.restore());
            }
        }
    }

    /^* Control: with the fallback disarmed the shift-click really does land in the locked slot. ^/
    private static void assertQuickMoveTargetsTheLockedSlot(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        arm(context, singleplayer, ServerModPresence.State.PRESENT);
        quickMoveSource(context);
        context.waitTicks(SETTLE_TICKS);

        ItemStack landed = hotbar(context, LOCKED_SLOT);
        if (landed.getItem() != Items.DIRT) {
            SmokeSupport.fail("the quick-move did not land in locked hotbar slot " + LOCKED_SLOT
                    + " (found " + landed.getItem() + ") - the eviction assertion below could not "
                    + "tell a working evictor from a quick-move that went somewhere else");
        }
        SmokeSupport.pass("a quick-move from the main inventory picks locked hotbar slot "
                + LOCKED_SLOT + " as its destination");
    }

    /^* The real assertion: armed, the same shift-click's destination is drained again. ^/
    private static void assertQuickMoveDestinationIsEvicted(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        arm(context, singleplayer, ServerModPresence.State.ABSENT);
        quickMoveSource(context);
        context.waitTicks(EVICTION_TICKS);

        ItemStack landed = hotbar(context, LOCKED_SLOT);
        if (!landed.isEmpty()) {
            SmokeSupport.fail("locked hotbar slot " + LOCKED_SLOT + " still holds "
                    + landed.getItem() + " after a quick-move chose it as its destination - the "
                    + "baseline diff no longer covers the shift-click path");
        }
        SmokeSupport.pass("a quick-move whose destination was locked hotbar slot " + LOCKED_SLOT
                + " was evicted again");
    }

    /^*
     * Puts the run into a known state: presence forced, inventory cleared and re-seeded, and
     * enough ticks for the evictor's baseline to adopt the empty hotbar before anything moves.
     ^/
    private static void arm(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            ServerModPresence.State state) {
        context.runOnClient(client -> ServerModPresence.forceState(ServerModPresence.State.PRESENT));
        SmokeSupport.clearInventory(singleplayer);
        SmokeSupport.placeServerSide(singleplayer, SOURCE_INVENTORY_SLOT, Items.DIRT, STACK_SIZE);
        context.waitTicks(SETTLE_TICKS);
        context.runOnClient(client -> ServerModPresence.forceState(state));
        context.waitTicks(SETTLE_TICKS);
    }

    /^*
     * Sends the shift-click itself. Straight through {@code gameMode} rather than through an open
     * screen: the packet is identical either way, and the screen hook has nothing to say about a
     * quick-move whose SOURCE is an ordinary unlocked slot - which is the whole point of this path.
     * Eviction is also suppressed while a screen is open, so there would be one to close again.
     ^/
    private static void quickMoveSource(ClientGameTestContext context) {
        context.runOnClient(client -> client.gameMode.handleInventoryMouseClick(
                INVENTORY_CONTAINER_ID, SOURCE_MENU_SLOT, 0, ClickType.QUICK_MOVE, client.player));
    }

    private static ItemStack hotbar(ClientGameTestContext context, int slot) {
        return context.computeOnClient(client -> client.player.getInventory().getItem(slot).copy());
    }
}
*///?}
