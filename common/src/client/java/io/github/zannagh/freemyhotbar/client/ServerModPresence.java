package io.github.zannagh.freemyhotbar.client;

import org.jspecify.annotations.Nullable;

/**
 * Tracks whether the server the client is connected to runs Free My Hotbar.
 *
 * <p>Only a server-side install can actually keep items out of a locked slot, so everything that
 * relocates items afterwards is gated on {@link State#ABSENT}: never on {@link State#PRESENT} (the
 * server already handles it) and never on {@link State#UNKNOWN} (the channel handshake may still be
 * in flight). The state is resolved by the loader glue and reset on disconnect.
 *
 * <p>{@link #forceState} exists for the in-game client tests only. FMH resolves the integrated
 * singleplayer server as {@link State#PRESENT} (it runs the mixin), and a Fabric client game test
 * has nothing BUT a singleplayer world - so without a way to say "pretend the server does not
 * have the mod", every fallback test would assert against a disarmed evictor and pass vacuously.
 * The override sits in FRONT of the published state rather than beside it, so a test drives the
 * one real evictor instead of a parallel copy, and the derivation keeps running untouched.
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

    /** Test-only override of {@link #state()}; null means the derived state is published. */
    private static volatile @Nullable State forcedState;

    private ServerModPresence() {
    }

    /**
     * Returns the current presence state.
     *
     * @return the state, never null.
     */
    public static State state() {
        State override = forcedState;
        return override != null ? override : state;
    }

    /**
     * Forces what {@link #state()} reports, for the in-game client tests only.
     *
     * <p>Not a second code path: the derivation keeps publishing into {@link #set}, and everything
     * that reads presence keeps reading {@link #state()}. Only the answer is substituted, which is
     * what lets a test drive the production evictor on the singleplayer world a client game test
     * always runs in.
     *
     * @param newState the state to report, or null to go back to the derived one.
     */
    public static void forceState(@Nullable State newState) {
        forcedState = newState;
    }

    /**
     * Sets the presence state.
     *
     * @param newState the resolved state; null is treated as {@link State#UNKNOWN}.
     */
    public static void set(State newState) {
        state = newState != null ? newState : State.UNKNOWN;
    }

    /** Resets to {@link State#UNKNOWN}, dropping any test override; call when leaving a world. */
    public static void reset() {
        state = State.UNKNOWN;
        forcedState = null;
    }
}
