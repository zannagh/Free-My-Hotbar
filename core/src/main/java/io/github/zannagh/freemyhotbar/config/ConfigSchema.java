package io.github.zannagh.freemyhotbar.config;

import java.util.ArrayList;
import java.util.List;

import io.github.zannagh.freemyhotbar.slot.SlotBlock;

/**
 * Version and defaulting rules for the client config file, kept Minecraft-free so the migration
 * behaviour can be unit tested.
 *
 * <p>Schema history: v1 stored the blocked slots as an integer bit mask, v2 replaced it with an
 * explicit slot list, v3 added the client-only fallback settings.
 */
public final class ConfigSchema {

    /** Current on-disk schema version. */
    public static final int CURRENT_VERSION = 3;

    /** Default for the "suppress my own clicks into locked slots" setting (schema v3). */
    public static final boolean DEFAULT_BLOCK_GUI_INTERACTIONS = true;

    /**
     * Default for the "evict immediately" setting (schema v3): off, because bypassing the movement
     * gate is what gets inventory clicks cancelled by anti-cheat.
     */
    public static final boolean DEFAULT_EVICT_IMMEDIATELY = false;

    private ConfigSchema() {
    }

    /**
     * Returns whether a file written with the given version must be rewritten after loading.
     *
     * @param version the {@code configVersion} read from disk.
     * @return true when the file predates {@link #CURRENT_VERSION}.
     */
    public static boolean needsRewrite(int version) {
        return version < CURRENT_VERSION;
    }

    /**
     * Decodes the legacy v1 bit mask into blocked slots.
     *
     * @param mask the stored mask; bit {@code i} means slot {@code i} is blocked.
     * @param slotCount the number of low bits to consider.
     * @return the blocked slots in ascending id order.
     */
    public static List<SlotBlock> decodeLegacyMask(int mask, int slotCount) {
        List<SlotBlock> blocked = new ArrayList<>();
        for (int id = 0; id < slotCount; id++) {
            if (((mask >> id) & 1) != 0) {
                blocked.add(new SlotBlock(id, true));
            }
        }
        return blocked;
    }

    /**
     * Applies the v3 default for a possibly absent {@code blockGuiInteractions} field.
     *
     * @param value the boxed value read from disk; null means the field was absent.
     * @return the stored value, or the default when absent.
     */
    public static boolean blockGuiInteractionsOrDefault(Boolean value) {
        return value != null ? value : DEFAULT_BLOCK_GUI_INTERACTIONS;
    }

    /**
     * Applies the v3 default for a possibly absent {@code evictImmediately} field.
     *
     * @param value the boxed value read from disk; null means the field was absent.
     * @return the stored value, or the default when absent.
     */
    public static boolean evictImmediatelyOrDefault(Boolean value) {
        return value != null ? value : DEFAULT_EVICT_IMMEDIATELY;
    }
}
