package io.github.zannagh.freemyhotbar.presence;

/**
 * Derives {@link PresenceState} from the capability handshake. Minecraft-free and pure so the
 * three outcomes can be unit-tested.
 */
public final class ServerPresenceResolver {

    private ServerPresenceResolver() {
    }

    /**
     * Resolves whether the connected server enforces this mod's slot locks.
     *
     * <p>{@code supportsChannel} must be the server's support for THIS mod's channel, not merely
     * whether the underlying library is present: a server running the library without Free My
     * Hotbar answers the handshake but has no handler for the locked-slots channel, so it enforces
     * nothing. Reading such a server as {@link PresenceState#PRESENT} would keep the client-side
     * fallback disarmed and leave the player's locks silently unenforced.
     *
     * @param resolved whether the capability handshake has completed (or timed out).
     * @param supportsChannel whether the server advertises a handler for this mod's channel.
     * @param singleplayer whether the client is playing on its own integrated server.
     * @return the resolved state, never null.
     */
    public static PresenceState resolve(boolean resolved, boolean supportsChannel, boolean singleplayer) {
        if (singleplayer) {
            // The integrated server runs the mod's own mixin, so locks are always enforced.
            return PresenceState.PRESENT;
        }
        if (!resolved) {
            return PresenceState.UNKNOWN;
        }
        return supportsChannel ? PresenceState.PRESENT : PresenceState.ABSENT;
    }
}
