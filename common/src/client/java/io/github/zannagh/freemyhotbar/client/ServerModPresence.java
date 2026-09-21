package io.github.zannagh.freemyhotbar.client;

/**
 * Tracks whether the server the client is connected to runs Free My Hotbar.
 *
 * <p>Only a server-side install can actually keep items out of a locked slot, so everything that
 * relocates items afterwards is gated on {@link State#ABSENT}: never on {@link State#PRESENT} (the
 * server already handles it) and never on {@link State#UNKNOWN} (the channel handshake may still be
 * in flight). The state is resolved by the loader glue and reset on disconnect.
 */
public final class ServerModPresence {

    /** Whether the remote side of the current connection has the mod. */
    public enum State {
        /** Not resolved yet; the channel handshake may still be in flight. */
        UNKNOWN,
        /** The server (or the integrated singleplayer server) runs the mod. */
        PRESENT,
        /** The server does not run the mod; only client-side fallbacks apply. */
        ABSENT
    }

    private static volatile State state = State.UNKNOWN;

    private ServerModPresence() {
    }

    /**
     * Returns the current presence state.
     *
     * @return the state, never null.
     */
    public static State state() {
        return state;
    }

    /**
     * Sets the presence state.
     *
     * @param newState the resolved state; null is treated as {@link State#UNKNOWN}.
     */
    public static void set(State newState) {
        state = newState != null ? newState : State.UNKNOWN;
    }

    /** Resets to {@link State#UNKNOWN}; call when leaving a world or server. */
    public static void reset() {
        state = State.UNKNOWN;
    }
}
