package io.github.zannagh.freemyhotbar.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.minecraft.client.Minecraft;

/**
 * Client-side persistence of the blocked hotbar slots (ids 0-8).
 *
 * <p>Stored as versioned JSON at {@code <gamedir>/config/free-my-hotbar.json}. Mutations are
 * fail-safe: a corrupt or missing file yields defaults. On every change the sync callback is fired
 * with the current slots so a loader can forward them to the server without common code referencing
 * any loader networking API.
 */
public final class ClientSlotConfig {

    /** Current on-disk schema version. */
    public static final int CURRENT_VERSION = 2;

    private static final int SLOT_COUNT = SlotBlock.HOTBAR_SLOT_COUNT;
    private static final String FILE_NAME = "free-my-hotbar.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<SlotBlock> blocked = new LinkedHashSet<>();

    private transient Consumer<List<SlotBlock>> syncCallback = s -> {
    };

    /** Serializable view written to and read from disk. */
    private static final class Data {
        int configVersion = CURRENT_VERSION;
        List<SlotBlock> slots;
        /** Legacy v1 field: boxed so a null means "absent". Read only during migration. */
        Integer mask;
    }

    /**
     * Loads the config from disk, returning a fail-safe default instance on any error.
     *
     * @return a populated config, never null.
     */
    public static ClientSlotConfig load() {
        ClientSlotConfig config = new ClientSlotConfig();
        Path path = configPath();
        if (path == null || !Files.exists(path)) {
            return config;
        }
        boolean migrated = false;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) {
                if (data.slots != null) {
                    for (SlotBlock slot : SlotBlock.blockedOnly(data.slots)) {
                        if (slot.slotId() >= 0 && slot.slotId() < SLOT_COUNT) {
                            config.blocked.add(slot);
                        }
                    }
                } else if (data.mask != null) {
                    // legacy v1 mask migration: decode the low SLOT_COUNT bits into slot ids.
                    for (int id = 0; id < SLOT_COUNT; id++) {
                        if (((data.mask >> id) & 1) != 0) {
                            config.blocked.add(new SlotBlock(id, true));
                        }
                    }
                    migrated = true;
                }
            }
        } catch (Exception e) {
            FreeMyHotbar.LOGGER.warn("Failed to read {}, using defaults", FILE_NAME, e);
        }
        if (migrated) {
            // Rewrite in the current (v2) format so the legacy mask field is dropped on disk.
            config.save();
        }
        return config;
    }

    /** Writes the current state to disk; failures are logged but never thrown. */
    public void save() {
        Path path = configPath();
        if (path == null) {
            return;
        }
        Data data = new Data();
        data.configVersion = CURRENT_VERSION;
        data.slots = slots();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            FreeMyHotbar.LOGGER.warn("Failed to write {}", FILE_NAME, e);
        }
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
        for (SlotBlock slot : SlotBlock.blockedOnly(in)) {
            if (slot.slotId() >= 0 && slot.slotId() < SLOT_COUNT) {
                blocked.add(slot);
            }
        }
        save();
        syncCallback.accept(slots());
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

    private static Path configPath() {
        try{
            return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
        } catch (NullPointerException ignored) {
            return null;
        }
    }
}
