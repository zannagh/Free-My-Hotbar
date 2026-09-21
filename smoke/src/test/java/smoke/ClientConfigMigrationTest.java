package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.zannagh.eunomia.configuration.FileConfigurationProvider;
import io.github.zannagh.freemyhotbar.config.ClientConfigData;
import io.github.zannagh.freemyhotbar.config.ConfigSchema;
import io.github.zannagh.freemyhotbar.config.FallbackMode;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end checks of the client config file: a real v1 and v2 document on disk, loaded through the
 * same {@link FileConfigurationProvider} the client uses, asserting both the resulting blocked set
 * and that the file is rewritten in the current shape. Real users have these files, so the migration
 * is proven against the bytes rather than against the decoding rules alone (those are
 * {@code ClientConfigSchemaTest}'s job).
 */
@DisplayName("Client config migration")
class ClientConfigMigrationTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfigMigrationTest.class);

    @Test
    @DisplayName("a v1 mask file migrates to v3 slots and is rewritten")
    void v1MaskFileMigrates(@TempDir Path dir) throws IOException {
        // 0b100000101 = 261 -> slots 0, 2 and 8.
        Path file = write(dir, "{\"configVersion\":1,\"mask\":261}");

        ClientConfigData loaded = load(file);

        assertEquals(List.of(new SlotBlock(0, true), new SlotBlock(2, true), new SlotBlock(8, true)),
                loaded.blockedSlots(), "the v1 mask bits must survive as blocked slots");
        assertEquals(FallbackMode.MOVE, loaded.fallbackMode(), "v1 knew no fallback mode");
        assertTrue(loaded.blockGuiInteractions(), "an absent blockGuiInteractions defaults to true");
        assertFalse(loaded.evictImmediately(), "an absent evictImmediately defaults to false");

        JsonObject rewritten = read(file);
        assertEquals(ConfigSchema.CURRENT_VERSION, rewritten.get("configVersion").getAsInt(),
                "the migrated file must be stamped with the current version");
        assertNull(rewritten.get("mask"), "the legacy mask field must be dropped on rewrite");
        assertEquals(SlotBlock.HOTBAR_SLOT_COUNT, rewritten.getAsJsonArray("slots").size(),
                "the rewrite must carry the full explicit slot list");
        assertEquals("MOVE", rewritten.get("fallbackMode").getAsString());
        assertTrue(rewritten.get("blockGuiInteractions").getAsBoolean());
        assertFalse(rewritten.get("evictImmediately").getAsBoolean());

        // The rewritten file is already current, so a second load leaves it alone and reads the same.
        assertEquals(loaded.blockedSlots(), load(file).blockedSlots(),
                "re-loading the migrated file must yield the same locks");
    }

    @Test
    @DisplayName("a v2 slot-list file keeps its locks and gains the v3 defaults")
    void v2FileMigrates(@TempDir Path dir) throws IOException {
        Path file = write(dir, "{\"configVersion\":2,\"slots\":["
                + "{\"slotId\":0,\"blocked\":false},"
                + "{\"slotId\":1,\"blocked\":true},"
                + "{\"slotId\":2,\"blocked\":false},"
                + "{\"slotId\":3,\"blocked\":true}]}");

        ClientConfigData loaded = load(file);

        assertEquals(List.of(new SlotBlock(1, true), new SlotBlock(3, true)), loaded.blockedSlots(),
                "the v2 blocked entries must survive verbatim");
        assertEquals(FallbackMode.MOVE, loaded.fallbackMode(), "v2 knew no fallback mode");
        assertTrue(loaded.blockGuiInteractions());
        assertFalse(loaded.evictImmediately());

        JsonObject rewritten = read(file);
        assertEquals(ConfigSchema.CURRENT_VERSION, rewritten.get("configVersion").getAsInt());
        assertEquals("MOVE", rewritten.get("fallbackMode").getAsString());
    }

    @Test
    @DisplayName("a v3 file is loaded verbatim and not rewritten")
    void v3FileIsLeftAlone(@TempDir Path dir) throws IOException {
        String json = "{\"configVersion\":3,\"slots\":[{\"slotId\":4,\"blocked\":true}],"
                + "\"fallbackMode\":\"move_or_drop\",\"blockGuiInteractions\":false,"
                + "\"evictImmediately\":true}";
        Path file = write(dir, json);

        ClientConfigData loaded = load(file);

        assertEquals(List.of(new SlotBlock(4, true)), loaded.blockedSlots());
        assertEquals(FallbackMode.MOVE_OR_DROP, loaded.fallbackMode(),
                "a hand-edited lower-case mode name must be honoured, not reset");
        assertFalse(loaded.blockGuiInteractions(), "a stored false must survive");
        assertTrue(loaded.evictImmediately(), "a stored true must survive");
        assertEquals(json, Files.readString(file, StandardCharsets.UTF_8),
                "a current file must not be rewritten on load");
    }

    @Test
    @DisplayName("a corrupt file falls back to defaults instead of throwing")
    void corruptFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        Path file = write(dir, "{ this is not json");

        ClientConfigData loaded = load(file);

        assertTrue(loaded.blockedSlots().isEmpty(), "a corrupt file must not invent locks");
        assertEquals(FallbackMode.DEFAULT, loaded.fallbackMode());
        assertTrue(loaded.blockGuiInteractions());
        assertFalse(loaded.evictImmediately());
    }

    @Test
    @DisplayName("a missing file yields defaults already stamped with the current version")
    void missingFileYieldsCurrentDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("free-my-hotbar.json");

        ClientConfigData loaded = load(file);

        assertTrue(loaded.blockedSlots().isEmpty());
        assertFalse(loaded.shouldMigrate(), "freshly written defaults must not ask to be migrated");
        assertEquals(ConfigSchema.CURRENT_VERSION, read(file).get("configVersion").getAsInt());
    }

    private static ClientConfigData load(Path file) {
        return new FileConfigurationProvider<>(
                file, ClientConfigData.class, ClientConfigData::defaults, GSON, LOGGER).getValue();
    }

    private static Path write(Path dir, String json) throws IOException {
        Path file = dir.resolve("free-my-hotbar.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
