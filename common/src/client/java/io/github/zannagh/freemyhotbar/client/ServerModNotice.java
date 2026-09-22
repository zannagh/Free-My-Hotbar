package io.github.zannagh.freemyhotbar.client;

import io.github.zannagh.freemyhotbar.config.FallbackMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Sends one honest chat notice per connection when the server does not run the mod and the player
 * has locked slots. Without a server-side install items ARE picked up into locked slots and can
 * only be moved out afterwards, so the message says exactly that.
 */
public final class ServerModNotice {

    private static boolean sent;

    private ServerModNotice() {
    }

    /** Re-arms the notice; call on disconnect so the next connection gets one. */
    public static void reset() {
        sent = false;
    }

    /**
     * Sends the notice if it is due. Cheap enough to call every client tick: it returns immediately
     * once the notice has been sent or while presence is still unresolved.
     *
     * @param minecraft the client instance; null is ignored.
     */
    public static void tick(Minecraft minecraft) {
        if (sent || minecraft == null) {
            return;
        }
        if (ServerModPresence.state() != ServerModPresence.State.ABSENT) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || !FreeMyHotbarClient.config().hasBlockedSlots()) {
            return;
        }
        sent = true;
        // 26.1 replaced the action-bar flag with a second method rather than renaming this one,
        // so this is a real signature change and not something the replacement table can carry.
        //? if >= 26.1 {
        /*player.sendSystemMessage(message());
        *///?} else {
        player.displayClientMessage(message(), false);
        //?}
    }

    private static Component message() {
        if (FreeMyHotbarClient.config().fallbackMode() == FallbackMode.OFF) {
            return Component.translatable("chat.free-my-hotbar.fallback_off");
        }
        return Component.translatable("chat.free-my-hotbar.fallback_notice");
    }
}
