package io.github.zannagh.freemyhotbar.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntConsumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import net.minecraft.client.Minecraft;

/**
 * Client-side persistence of the 9-bit hotbar lock mask (bits 0-8).
 *
 * <p>Stored as versioned JSON at {@code <gamedir>/config/free-my-hotbar.json}. Mutations are
 * fail-safe: a corrupt or missing file yields defaults. On every change the {@link IntConsumer}
 * sync callback is fired with the new mask so a loader can forward it to the server without common
 * code referencing any loader networking API.
 */
public final class ClientSlotConfig {

    /** Current on-disk schema version. */
    public static final int CURRENT_VERSION = 1;

    private static final int SLOT_COUNT = 9;
    private static final int MASK_BITS = (1 << SLOT_COUNT) - 1;
    private static final String FILE_NAME = "free-my-hotbar.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int configVersion = CURRENT_VERSION;
    private int mask;

    private transient IntConsumer syncCallback = m -> {
    };

    /** Serializable view written to and read from disk. */
    private static final class Data {
        int configVersion = CURRENT_VERSION;
        int mask;
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
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) {
                config.configVersion = CURRENT_VERSION;
                config.mask = data.mask & MASK_BITS;
            }
        } catch (Exception e) {
            FreeMyHotbar.LOGGER.warn("Failed to read {}, using defaults", FILE_NAME, e);
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
        data.configVersion = configVersion;
        data.mask = mask & MASK_BITS;
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
     * Returns the raw 9-bit mask (bits 0-8).
     *
     * @return the current mask.
     */
    public int mask() {
        return mask;
    }

    /**
     * Returns whether the given hotbar slot is locked.
     *
     * @param slot hotbar slot index 0-8.
     * @return true if the slot's bit is set.
     */
    public boolean isLocked(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        return (mask & (1 << slot)) != 0;
    }

    /**
     * Flips the lock bit for a slot, saves, then fires the sync callback.
     *
     * @param slot hotbar slot index; ignored when outside 0-8.
     */
    public void toggle(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        mask ^= (1 << slot);
        mask &= MASK_BITS;
        save();
        syncCallback.accept(mask);
    }

    /**
     * Replaces the whole mask, saves, then fires the sync callback.
     *
     * @param newMask the new mask; clamped to bits 0-8.
     */
    public void set(int newMask) {
        mask = newMask & MASK_BITS;
        save();
        syncCallback.accept(mask);
    }

    /**
     * Sets the sync callback invoked with the new mask after every mutation.
     *
     * @param callback consumer of the mask; null resets to a no-op.
     */
    public void setSyncCallback(IntConsumer callback) {
        syncCallback = callback != null ? callback : m -> {
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
