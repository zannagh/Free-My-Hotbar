//? if fcgt {
/*package io.github.zannagh.freemyhotbar.smoke;

import java.util.ArrayList;
import java.util.List;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.HotbarEvictor;
import io.github.zannagh.freemyhotbar.client.ServerModPresence;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/^*
 * The pieces every Free My Hotbar client game test needs: a world to stand in, a way to put an
 * item somewhere the way a server would, and a snapshot of the client config to put back
 * afterwards.
 *
 * <p>All of them run in ONE client launch, one after the other, against the same config file and
 * the same static mod state — so a test that leaves a slot locked or the presence override forced
 * silently changes what the next one is asserting. Every test therefore takes a {@link Snapshot}
 * first and restores it in a {@code finally}.
 ^/
public final class SmokeSupport {

    /^* Shared log prefix, so a failing run is greppable in the client log. ^/
    public static final String TAG = "[smoke/fcgt]";

    private SmokeSupport() {
    }

    /^*
     * Creates the singleplayer world every test runs in: SURVIVAL and structureless.
     *
     * <p>Survival deliberately, even though nothing here needs to survive anything: in creative the
     * inventory key opens {@code CreativeModeInventoryScreen}, whose menu is the item-tab menu, so
     * menu slot 36 is whatever block the current tab shows there rather than hotbar slot 0. Items
     * are placed straight onto the server player anyway, so creative buys nothing.
     *
     * @param context the game test context.
     * @return the singleplayer context; close it to leave the world.
     ^/
    public static TestSingleplayerContext createWorld(ClientGameTestContext context) {
        // One tick of slack so the previous test's world teardown has finished settling before
        // the world builder starts driving the create-world screens.
        context.waitTicks(1);
        return context.worldBuilder()
                .setUseConsistentSettings(true)
                .adjustSettings(state -> {
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setGenerateStructures(false);
                })
                .create();
    }

    /^*
     * Puts a stack into one of the player's inventory slots ON THE SERVER and broadcasts it, which
     * is exactly the shape an auto-pickup has from the client's point of view: the slot simply
     * grows, with no click of the player's behind it.
     *
     * @param singleplayer the singleplayer context.
     * @param slot the {@code Inventory} index (0-8 hotbar, 9-35 main).
     * @param item the item to place.
     * @param count the stack size.
     ^/
    public static void placeServerSide(TestSingleplayerContext singleplayer, int slot, Item item, int count) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.getInventory().setItem(slot, new ItemStack(item, count));
            player.inventoryMenu.broadcastChanges();
        });
    }

    /^*
     * Empties the player's inventory on the server, so a test starts from a known state whatever
     * the previous one left behind.
     *
     * @param singleplayer the singleplayer context.
     ^/
    public static void clearInventory(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.getInventory().clearContent();
            player.inventoryMenu.broadcastChanges();
        });
    }

    /^*
     * Locks exactly the given hotbar slots and unlocks every other one.
     *
     * @param slots the hotbar indices to lock.
     ^/
    public static void lockOnly(int... slots) {
        List<SlotBlock> blocked = new ArrayList<>();
        for (int slot : slots) {
            blocked.add(new SlotBlock(slot, true));
        }
        FreeMyHotbarClient.config().setBlocked(blocked);
    }

    /^* Throws an {@link IllegalStateException} tagged for the log. ^/
    public static void fail(String message) {
        throw new IllegalStateException(TAG + " " + message);
    }

    /^* Logs a passed assertion, so the run output says what was actually proven. ^/
    public static void pass(String message) {
        FreeMyHotbar.LOGGER.info("{} PASS {}", TAG, message);
    }

    /^*
     * Everything a test may change that outlives it: the client config (which is persisted to
     * disk) and the test-only presence override.
     *
     * @param blocked the locked slots at capture time.
     * @param blockGuiInteractions the GUI-blocking setting at capture time.
     * @param evictImmediately the movement-gate setting at capture time.
     ^/
    public record Snapshot(List<SlotBlock> blocked, boolean blockGuiInteractions,
                           boolean evictImmediately) {

        /^*
         * Captures the current client config.
         *
         * @return the snapshot to restore later.
         ^/
        public static Snapshot capture() {
            return new Snapshot(
                    FreeMyHotbarClient.config().slots(),
                    FreeMyHotbarClient.config().blockGuiInteractions(),
                    FreeMyHotbarClient.config().evictImmediately());
        }

        /^* Puts the config back, drops the presence override and clears the evictor's state. ^/
        public void restore() {
            ServerModPresence.forceState(null);
            FreeMyHotbarClient.config().setBlockGuiInteractions(blockGuiInteractions);
            FreeMyHotbarClient.config().setEvictImmediately(evictImmediately);
            FreeMyHotbarClient.config().setBlocked(blocked);
            HotbarEvictor.reset();
        }
    }
}
*///?}
