package io.github.zannagh.freemyhotbar.client.config;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.zannagh.eunomia.configuration.ConfigurationProvider;
import de.zannagh.eunomia.configuration.FileConfigurationProvider;
import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.client.HotbarEvictor;
import io.github.zannagh.freemyhotbar.config.ClientConfigData;
import io.github.zannagh.freemyhotbar.config.ConfigSchema;
import io.github.zannagh.freemyhotbar.config.FallbackMode;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Client-side persistence of the blocked hotbar slots (ids 0-8).
 *
 * <p>Stored as versioned JSON at {@code <gamedir>/config/free-my-hotbar.json}. Load, save and the
 * v1-mask -> v2 -> v3 migration are eunomia's: {@link FileConfigurationProvider} owns the file and
 * {@link ClientConfigData} owns the document, so this class is the in-memory view plus the mod's own
 * rules (slot-range filtering, the eviction reset, the sync callback). Mutations stay fail-safe: a
 * corrupt or missing file yields defaults. On every change to the slots the sync callback is fired
 * with the current slots so a loader can forward them to the server without common code referencing
 * any loader networking API.
 */
public final class ClientSlotConfig {

    /** Current on-disk schema version. */
    public static final int CURRENT_VERSION = ConfigSchema.CURRENT_VERSION;

    private static final int SLOT_COUNT = SlotBlock.HOTBAR_SLOT_COUNT;
    private static final String FILE_NAME = "free-my-hotbar.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<SlotBlock> blocked = new LinkedHashSet<>();

    /**
     * The file-backed store, or null when there is no game directory to write into (a headless or
     * not-yet-initialised client). All state then stays in memory and {@link #save()} is a no-op,
     * which is what the pre-eunomia implementation did with a null path too.
     */
    private final @Nullable ConfigurationProvider<ClientConfigData> provider;

    private FallbackMode fallbackMode = FallbackMode.DEFAULT;
    private boolean blockGuiInteractions = ConfigSchema.DEFAULT_BLOCK_GUI_INTERACTIONS;
    private boolean evictImmediately = ConfigSchema.DEFAULT_EVICT_IMMEDIATELY;

    private Consumer<List<SlotBlock>> syncCallback = s -> {
    };

    private ClientSlotConfig(@Nullable ConfigurationProvider<ClientConfigData> provider) {
        this.provider = provider;
        if (provider != null) {
            apply(provider.getValue());
        }
    }

    /**
     * Loads the config from disk, returning a fail-safe default instance on any error.
     *
     * <p>The provider migrates a legacy document and rewrites the file itself when it did, so a v1
     * mask file is read as slots and re-saved in the current shape without this class knowing.
     *
     * @return a populated config, never null.
     */
    public static ClientSlotConfig load() {
        Path path = configPath();
        if (path == null) {
            return new ClientSlotConfig(null);
        }
        try {
            return new ClientSlotConfig(new FileConfigurationProvider<>(
                    path, ClientConfigData.class, ClientConfigData::defaults, GSON, FreeMyHotbar.LOGGER));
        } catch (Exception e) {
            // The provider already swallows a corrupt document; this covers the file system itself
            // being unusable, which must not stop the client from starting with defaults.
            FreeMyHotbar.LOGGER.warn("Failed to open {}, using defaults", FILE_NAME, e);
            return new ClientSlotConfig(null);
        }
    }

    /** Copies one loaded document into this instance. */
    private void apply(ClientConfigData data) {
        addBlocked(data.blockedSlots());
        fallbackMode = data.fallbackMode();
        blockGuiInteractions = data.blockGuiInteractions();
        evictImmediately = data.evictImmediately();
    }

    private void addBlocked(Collection<SlotBlock> slots) {
        for (SlotBlock slot : slots) {
            if (slot.blocked() && slot.slotId() >= 0 && slot.slotId() < SLOT_COUNT) {
                blocked.add(slot);
            }
        }
    }

    /** Writes the current state to disk; failures are logged by the provider but never thrown. */
    public void save() {
        if (provider == null) {
            return;
        }
        provider.updateAndSave(ClientConfigData.current(
                slots(), fallbackMode, blockGuiInteractions, evictImmediately));
    }

    /**
     * Returns the slots 0-8 with their blocked state, in ascending id order.
     *
     * @return the current slot list.
     */
    public List<SlotBlock> slots() {
        return SlotBlock.fullList(blocked, SLOT_COUNT);
    }

    /**
     * Returns whether the given hotbar slot is blocked.
     *
     * @param slot hotbar slot index 0-8.
     * @return true if the slot is blocked.
     */
    public boolean isBlocked(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        return blocked.contains(new SlotBlock(slot, true));
    }

    /**
     * Toggles the blocked state of a slot, saves, then fires the sync callback.
     *
     * @param slot hotbar slot index; ignored when outside 0-8.
     */
    public void toggle(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        SlotBlock b = new SlotBlock(slot, true);
        if (!blocked.remove(b)) {
            blocked.add(b);
        }
        save();
        // The fallback's per-slot drop budget is a judgement about the slots as they were; the
        // player just changed them, so give it a clean slate.
        HotbarEvictor.reset();
        syncCallback.accept(slots());
    }

    /**
     * Replaces the blocked slots with a filtered copy (blocked-only, ids 0-8), saves, then fires
     * the sync callback.
     *
     * @param in the new slots; non-blocked entries and ids outside 0-8 are dropped.
     */
    public void setBlocked(Collection<SlotBlock> in) {
        blocked.clear();
        addBlocked(SlotBlock.blockedOnly(in));
        save();
        HotbarEvictor.reset();
        syncCallback.accept(slots());
    }

    /**
     * Returns whether any hotbar slot is currently locked.
     *
     * @return true when at least one slot is blocked.
     */
    public boolean hasBlockedSlots() {
        return !blocked.isEmpty();
    }

    /**
     * Returns the client-only fallback behaviour used on servers without the mod.
     *
     * @return the configured mode, never null.
     */
    public FallbackMode fallbackMode() {
        return fallbackMode;
    }

    /**
     * Sets the client-only fallback behaviour and saves.
     *
     * @param mode the new mode; null resets to the default.
     */
    public void setFallbackMode(FallbackMode mode) {
        fallbackMode = FallbackMode.orDefault(mode);
        save();
        HotbarEvictor.reset();
    }

    /**
     * Returns whether the player's own clicks into locked slots are suppressed in inventory screens.
     *
     * @return true when GUI interactions with locked slots are blocked.
     */
    public boolean blockGuiInteractions() {
        return blockGuiInteractions;
    }

    /**
     * Sets whether the player's own clicks into locked slots are suppressed, and saves.
     *
     * @param value the new setting.
     */
    public void setBlockGuiInteractions(boolean value) {
        blockGuiInteractions = value;
        save();
    }

    /**
     * Returns whether the fallback evictor may click while the player is moving.
     *
     * <p>Off by default. The movement gate exists because anti-cheat plugins cancel container
     * clicks sent by a moving player; turning it off evicts sooner but can make evictions silently
     * fail on such servers. See {@code EvictionPolicy.clickGateOpen}.
     *
     * @return true when the movement gate is bypassed.
     */
    public boolean evictImmediately() {
        return evictImmediately;
    }

    /**
     * Sets whether the fallback evictor bypasses the movement gate, and saves.
     *
     * @param value the new setting.
     */
    public void setEvictImmediately(boolean value) {
        evictImmediately = value;
        save();
        HotbarEvictor.reset();
    }

    /**
     * Sets the sync callback invoked with the current slots after every mutation.
     *
     * @param callback consumer of the slot list; null resets to a no-op.
     */
    public void setSyncCallback(Consumer<List<SlotBlock>> callback) {
        syncCallback = callback != null ? callback : s -> {
        };
    }

    private static @Nullable Path configPath() {
        try {
            return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
        } catch (NullPointerException ignored) {
            return null;
        }
    }
}
