package io.github.zannagh.freemyhotbar.client;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import io.github.zannagh.freemyhotbar.net.FmhPackets;
import io.github.zannagh.freemyhotbar.presence.PresenceState;
import io.github.zannagh.freemyhotbar.presence.ServerPresenceResolver;
import net.minecraft.client.Minecraft;

/**
 * Feeds {@link ServerModPresence} from eunomia's capability handshake, once per client tick.
 *
 * <p>The handshake is what tells the client which channels the server has handlers for. It is
 * deliberately read as "does the server handle OUR channel", not "does the server run eunomia":
 * a server with eunomia but without Free My Hotbar answers the handshake while enforcing nothing,
 * and treating it as PRESENT would leave the client-side fallback disarmed. The derivation itself
 * is the pure {@link ServerPresenceResolver} in the MC-free core.
 *
 * <p>Polling per tick instead of subscribing to the resolution callback keeps one code path for
 * all three outcomes, including the handshake timeout that resolves an unreachable server as
 * absent.
 */
public final class ServerPresenceTracker {

    private ServerPresenceTracker() {
    }

    /**
     * Re-derives and publishes the presence state. Cheap enough to call every client tick.
     *
     * @param minecraft the client instance; ignored while there is no player (i.e. no connection),
     *     which leaves the state as the disconnect handler reset it.
     */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        ServerCapabilities capabilities = CommunicationManager.serverCapabilities();
        if (capabilities == null) {
            return;
        }
        PresenceState resolved = ServerPresenceResolver.resolve(
                capabilities.isResolved(),
                capabilities.supports(FmhPackets.LOCKED_SLOTS),
                minecraft.hasSingleplayerServer());
        ServerModPresence.set(toPresence(resolved));
    }

    private static ServerModPresence.State toPresence(PresenceState state) {
        return switch (state) {
            case PRESENT -> ServerModPresence.State.PRESENT;
            case ABSENT -> ServerModPresence.State.ABSENT;
            case UNKNOWN -> ServerModPresence.State.UNKNOWN;
        };
    }
}
