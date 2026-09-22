//? if fcgt {
/*package io.github.zannagh.freemyhotbar.smoke;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.ServerModPresence;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/^*
 * The two assertions the client-side fallback exists for, driven end to end through the real
 * {@code HotbarEvictor} on a real (integrated) server.
 *
 * <ol>
 *   <li>An item that ARRIVES in a locked hotbar slot is moved out into the main inventory.
 *   <li>A tool the player already keeps in a DIFFERENT locked slot is left exactly where it is.
 * </ol>
 *
 * <p>The second is the regression guard for the bug where the sweep evicted every occupied locked
 * slot: one stack landing in slot 6 yanked the sword out of slot 1 as well. Both are asserted from
 * the same world state, so the sweep that drains the arrival is literally the sweep that must not
 * touch the sword.
 *
 * <p>The fallback only runs when the server does NOT have the mod, and a client game test has
 * nothing but a singleplayer world - whose integrated server always does. {@link ServerModPresence}
 * carries a test-only override for exactly this; without it this test would assert against a
 * disarmed evictor and pass having exercised nothing.
 ^/
public final class LockedSlotEvictionSmokeTest implements FabricClientGameTest {

    /^* Locked, and holds the player's own tool from before the locks went on. ^/
    private static final int RESERVED_SLOT = 0;

    /^* Locked, and empty until the "pickup" lands in it. ^/
    private static final int ARRIVAL_SLOT = 5;

    /^* First and last {@code Inventory} index of the main (non-hotbar) inventory. ^/
    private static final int MAIN_FIRST = 9;
    private static final int MAIN_LAST = 35;

    private static final int SETTLE_TICKS = 10;
    private static final int EVICTION_TICKS = 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        FreeMyHotbar.LOGGER.info("{} locked-slot eviction starting", SmokeSupport.TAG);
        context.waitForScreen(TitleScreen.class);
        SmokeSupport.Snapshot snapshot = SmokeSupport.Snapshot.capture();
        try (TestSingleplayerContext singleplayer = SmokeSupport.createWorld(context)) {
            try {
                seedReservedTool(context, singleplayer);
                assertArrivalIsEvicted(context, singleplayer);
                assertReservedToolSurvived(context);
            } finally {
                context.runOnClient(client -> snapshot.restore());
            }
        }
    }

    /^*
     * Puts a sword in one locked slot the way the player would have: while the fallback is
     * disarmed, so the evictor's baseline adopts it as the slot's normal contents rather than
     * seeing it as something that arrived.
     ^/
    private static void seedReservedTool(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> ServerModPresence.forceState(ServerModPresence.State.PRESENT));
        SmokeSupport.clearInventory(singleplayer);
        SmokeSupport.placeServerSide(singleplayer, RESERVED_SLOT, Items.DIAMOND_SWORD, 1);
        // Locking resets the evictor, so the baseline is re-learned AFTER this - which is exactly
        // the situation being tested: locks on, a tool already sitting in one of them.
        context.runOnClient(client -> SmokeSupport.lockOnly(RESERVED_SLOT, ARRIVAL_SLOT));
        context.waitTicks(SETTLE_TICKS);
        if (hotbar(context, RESERVED_SLOT).getItem() != Items.DIAMOND_SWORD) {
            SmokeSupport.fail("the reserved sword never reached client hotbar slot " + RESERVED_SLOT
                    + " - the rest of this test would be meaningless");
        }
    }

    /^* Arms the fallback, drops an item into the other locked slot and waits for the sweep. ^/
    private static void assertArrivalIsEvicted(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> ServerModPresence.forceState(ServerModPresence.State.ABSENT));
        context.waitTicks(SETTLE_TICKS);
        SmokeSupport.placeServerSide(singleplayer, ARRIVAL_SLOT, Items.DIRT, 1);
        context.waitTicks(EVICTION_TICKS);

        ItemStack arrival = hotbar(context, ARRIVAL_SLOT);
        if (!arrival.isEmpty()) {
            SmokeSupport.fail("locked hotbar slot " + ARRIVAL_SLOT + " still holds "
                    + arrival.getItem() + " - the arrival was never evicted");
        }
        if (!mainInventoryHoldsDirt(context)) {
            SmokeSupport.fail("the evicted stack is not in the main inventory - it vanished instead "
                    + "of being quick-moved");
        }
        SmokeSupport.pass("an arrival into locked hotbar slot " + ARRIVAL_SLOT
                + " was evicted into the main inventory");
    }

    /^* The F1 regression: the sweep that drained the arrival must not have touched the sword. ^/
    private static void assertReservedToolSurvived(ClientGameTestContext context) {
        ItemStack reserved = hotbar(context, RESERVED_SLOT);
        if (reserved.getItem() != Items.DIAMOND_SWORD) {
            SmokeSupport.fail("the reserved sword in locked hotbar slot " + RESERVED_SLOT
                    + " was evicted by a pickup into slot " + ARRIVAL_SLOT + " (found "
                    + reserved.getItem() + ") - the sweep is back to draining every occupied "
                    + "locked slot instead of only what arrived");
        }
        SmokeSupport.pass("the reserved sword in locked hotbar slot " + RESERVED_SLOT
                + " survived a pickup into locked slot " + ARRIVAL_SLOT);
    }

    private static ItemStack hotbar(ClientGameTestContext context, int slot) {
        return context.computeOnClient(client -> client.player.getInventory().getItem(slot).copy());
    }

    private static boolean mainInventoryHoldsDirt(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            for (int slot = MAIN_FIRST; slot <= MAIN_LAST; slot++) {
                if (client.player.getInventory().getItem(slot).getItem() == Items.DIRT) {
                    return true;
                }
            }
            return false;
        });
    }
}
*///?}
