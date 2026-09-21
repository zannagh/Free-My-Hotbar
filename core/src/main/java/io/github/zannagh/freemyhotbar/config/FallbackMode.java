package io.github.zannagh.freemyhotbar.config;

import java.util.Locale;

/**
 * What the client does with an item that has already landed in a locked hotbar slot on a server
 * that does not run the mod. Pickup itself cannot be prevented from a client-only install, so the
 * fallback can only relocate afterwards.
 */
public enum FallbackMode {

    /** Do nothing; locked slots are not enforced on a mod-less server. */
    OFF,

    /** Move the item into the main inventory; leave it in place when the inventory is full. */
    MOVE,

    /** Move the item into the main inventory, or throw it back on the ground when that is full. */
    MOVE_OR_DROP;

    /** The mode used for a fresh config and whenever a stored value cannot be understood. */
    public static final FallbackMode DEFAULT = MOVE;

    /**
     * Returns the next mode in declaration order, wrapping around. Used by the cycle button in the
     * slot-lock screen.
     *
     * @return the following mode.
     */
    public FallbackMode next() {
        FallbackMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Returns the translation key for this mode's display name.
     *
     * @return the lower-cased key under {@code screen.free-my-hotbar.fallback}.
     */
    public String translationKey() {
        return "screen.free-my-hotbar.fallback." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a stored mode name, falling back to {@link #DEFAULT}.
     *
     * @param name the stored name; may be null or unknown.
     * @return the parsed mode, never null.
     */
    public static FallbackMode parse(String name) {
        if (name == null) {
            return DEFAULT;
        }
        for (FallbackMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name.trim())) {
                return mode;
            }
        }
        return DEFAULT;
    }

    /**
     * Returns the given mode, or {@link #DEFAULT} when it is absent.
     *
     * @param mode the mode read from disk; may be null.
     * @return a usable mode, never null.
     */
    public static FallbackMode orDefault(FallbackMode mode) {
        return mode != null ? mode : DEFAULT;
    }
}
