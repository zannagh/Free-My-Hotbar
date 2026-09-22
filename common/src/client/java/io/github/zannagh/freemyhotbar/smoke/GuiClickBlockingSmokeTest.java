//? if fcgt {
/*package io.github.zannagh.freemyhotbar.smoke;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.ClientScreens;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.ServerModPresence;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/^*
 * The inbound-only GUI guard, driven through the real {@code slotClicked} hook in an inventory
 * screen the player opened with the inventory key.
 *
 * <p>Three clicks on the SAME locked slot with the same stack, which is what makes each one mean
 * something:
 * <ol>
 *   <li>taking the item OUT succeeds - a locked slot must never trap its contents;
 *   <li>putting the carried stack back IN is refused while {@code blockGuiInteractions} is on;
 *   <li>the identical click goes through once the setting is off - so (2) failed because of the
 *       setting, not because the simulated click never reached the screen.
 * </ol>
 *
 * <p>Presence is forced to PRESENT throughout: this is about the client-side input guard, and an
 * armed fallback evictor moving items around underneath would only add noise.
 ^/
public final class GuiClickBlockingSmokeTest implements FabricClientGameTest {

    private static final int LOCKED_SLOT = 0;

    /^* {@code InventoryMenu} slot index of hotbar slot 0. ^/
    private static final int LOCKED_MENU_SLOT = 36;

    private static final int STACK_SIZE = 4;
    private static final int SETTLE_TICKS = 10;

    /^* The one {@code slotClicked} overload the mod hooks, identified by its parameters. ^/
    private static final Class<?>[] SLOT_CLICKED_PARAMETERS = {
        Slot.class, int.class, int.class, ClickType.class
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        FreeMyHotbar.LOGGER.info("{} GUI click blocking starting", SmokeSupport.TAG);
        context.waitForScreen(TitleScreen.class);
        SmokeSupport.Snapshot snapshot = SmokeSupport.Snapshot.capture();
        try (TestSingleplayerContext singleplayer = SmokeSupport.createWorld(context)) {
            try {
                openInventoryOnLockedSlot(context, singleplayer);
                assertTakingOutIsAllowed(context);
                assertPuttingInIsRefused(context);
                assertPuttingInWorksWithTheGuardOff(context);
            } finally {
                context.setScreen(() -> null);
                context.runOnClient(client -> snapshot.restore());
            }
        }
    }

    private static void openInventoryOnLockedSlot(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> {
            ServerModPresence.forceState(ServerModPresence.State.PRESENT);
            SmokeSupport.lockOnly(LOCKED_SLOT);
            FreeMyHotbarClient.config().setBlockGuiInteractions(true);
        });
        SmokeSupport.clearInventory(singleplayer);
        SmokeSupport.placeServerSide(singleplayer, LOCKED_SLOT, Items.DIRT, STACK_SIZE);
        context.waitTicks(SETTLE_TICKS);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTicks(SETTLE_TICKS);
        context.runOnClient(client -> {
            if (!(ClientScreens.current(client) instanceof AbstractContainerScreen)) {
                SmokeSupport.fail("the inventory key did not open a container screen");
            }
        });
    }

    /^* Outbound is always allowed: a locked slot refuses arrivals, it does not trap its contents. ^/
    private static void assertTakingOutIsAllowed(ClientGameTestContext context) {
        clickLockedSlot(context);
        check(context, Items.DIRT, Items.AIR,
                "taking the stack OUT of locked hotbar slot " + LOCKED_SLOT + " was refused");
        SmokeSupport.pass("taking an item out of a locked slot is still allowed");
    }

    /^* The guard itself: the same stack may not go back into the locked slot. ^/
    private static void assertPuttingInIsRefused(ClientGameTestContext context) {
        clickLockedSlot(context);
        check(context, Items.DIRT, Items.AIR,
                "a click placing the carried stack INTO locked hotbar slot " + LOCKED_SLOT
                        + " was not refused");
        SmokeSupport.pass("a click placing a carried stack into a locked slot is refused");
    }

    /^* Control: with the setting off the identical click goes through. ^/
    private static void assertPuttingInWorksWithTheGuardOff(ClientGameTestContext context) {
        context.runOnClient(client -> FreeMyHotbarClient.config().setBlockGuiInteractions(false));
        clickLockedSlot(context);
        check(context, Items.AIR, Items.DIRT,
                "with the guard off the same click still did not place the stack - the refusal "
                        + "above proves nothing about the setting");
        SmokeSupport.pass("with the guard off the identical click places the stack");
    }

    /^*
     * Asserts what the cursor and the locked slot hold.
     *
     * @param expectedCarried the item expected on the cursor.
     * @param expectedSlot the item expected in the locked slot.
     * @param message what went wrong if they do not match.
     ^/
    private static void check(ClientGameTestContext context, Item expectedCarried,
            Item expectedSlot, String message) {
        ItemStack carried = carried(context);
        ItemStack slot = hotbar(context, LOCKED_SLOT);
        if (carried.getItem() != expectedCarried || slot.getItem() != expectedSlot) {
            SmokeSupport.fail(message + " (cursor=" + carried.getItem() + ", slot="
                    + slot.getItem() + ")");
        }
    }

    /^* Sends a plain left click on the locked slot through the screen's real click entry point. ^/
    private static void clickLockedSlot(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Screen screen = ClientScreens.current(client);
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
                SmokeSupport.fail("the container screen closed unexpectedly");
                return;
            }
            Slot slot = containerScreen.getMenu().slots.get(LOCKED_MENU_SLOT);
            invokeSlotClicked(containerScreen, slot);
        });
        context.waitTicks(SETTLE_TICKS);
    }

    /^*
     * Calls {@code AbstractContainerScreen#slotClicked}, which is what every click, drag step and
     * number-key swap funnels through and where the mod's hook sits.
     *
     * <p>Reflection, and deliberately matched on the PARAMETER TYPES rather than on the name: the
     * method is protected, and 26.3 adds a second overload under the same name. The four-argument
     * shape is unique in every supported version, which is the same identity the mixin's own
     * descriptor constant pins.
     ^/
    private static void invokeSlotClicked(AbstractContainerScreen<?> screen, Slot slot) {
        for (Method candidate : AbstractContainerScreen.class.getDeclaredMethods()) {
            if (!Arrays.equals(candidate.getParameterTypes(), SLOT_CLICKED_PARAMETERS)) {
                continue;
            }
            candidate.setAccessible(true);
            try {
                candidate.invoke(screen, slot, LOCKED_MENU_SLOT, 0, ClickType.PICKUP);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(SmokeSupport.TAG + " slotClicked threw", e);
            }
            return;
        }
        SmokeSupport.fail("AbstractContainerScreen has no (Slot, int, int, ClickType) method - "
                + "the mod's own @Inject target would be gone too");
    }

    private static ItemStack carried(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.player.containerMenu.getCarried().copy());
    }

    private static ItemStack hotbar(ClientGameTestContext context, int slot) {
        return context.computeOnClient(client -> client.player.getInventory().getItem(slot).copy());
    }
}
*///?}
