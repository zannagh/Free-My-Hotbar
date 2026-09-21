package io.github.zannagh.freemyhotbar.presence;

/** Whether the remote side of the current connection enforces Free My Hotbar's slot locks. */
public enum PresenceState {

    /** Not resolved yet; the capability handshake may still be in flight. */
    UNKNOWN,

    /** The server (or the integrated singleplayer server) handles the mod's packets. */
    PRESENT,

    /** The server does not handle the mod's packets; only client-side fallbacks apply. */
    ABSENT
}
