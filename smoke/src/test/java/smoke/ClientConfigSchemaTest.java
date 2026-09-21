package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zannagh.freemyhotbar.config.ConfigSchema;
import io.github.zannagh.freemyhotbar.config.FallbackMode;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Plain-JVM checks for the client config's versioning and defaulting rules. The rules live in the
 * MC-free :core module precisely so the migration behaviour can be tested without booting a client.
 */
@DisplayName("Client config schema")
class ClientConfigSchemaTest {

    @Test
    @DisplayName("the current schema version is 3")
    void currentVersionIsThree() {
        assertEquals(3, ConfigSchema.CURRENT_VERSION, "schema v3 added the client fallback settings");
    }

    @Test
    @DisplayName("v1 and v2 files are rewritten, v3 files are not")
    void olderFilesAreRewritten() {
        assertTrue(ConfigSchema.needsRewrite(1), "a v1 mask file must be rewritten");
        assertTrue(ConfigSchema.needsRewrite(2), "a v2 file must be rewritten to persist the new fields");
        assertFalse(ConfigSchema.needsRewrite(3), "a current file must not be rewritten on load");
    }

    @Test
    @DisplayName("v2 -> v3 defaults the new fields")
    void v2ToV3Defaults() {
        // A v2 file carries neither field, which Gson leaves null.
        assertEquals(FallbackMode.MOVE, FallbackMode.orDefault(null),
                "an absent fallbackMode must default to MOVE");
        assertTrue(ConfigSchema.blockGuiInteractionsOrDefault(null),
                "an absent blockGuiInteractions must default to true");
        assertFalse(ConfigSchema.evictImmediatelyOrDefault(null),
                "an absent evictImmediately must default to false: the movement gate stays on");

        // Values already on disk survive.
        assertEquals(FallbackMode.OFF, FallbackMode.orDefault(FallbackMode.OFF));
        assertFalse(ConfigSchema.blockGuiInteractionsOrDefault(Boolean.FALSE));
        assertTrue(ConfigSchema.evictImmediatelyOrDefault(Boolean.TRUE));
    }

    @Test
    @DisplayName("stored fallback mode names are parsed case-insensitively, unknown ones default")
    void unknownFallbackModeParsesToDefault() {
        // parse() is the real read path (ClientSlotConfig stores the mode as a String), so a
        // hand-edited lower-case value in the config file is honoured rather than silently reset.
        assertEquals(FallbackMode.MOVE_OR_DROP, FallbackMode.parse("move_or_drop"));
        assertEquals(FallbackMode.MOVE_OR_DROP, FallbackMode.parse(" MOVE_OR_DROP "));
        assertEquals(FallbackMode.OFF, FallbackMode.parse("off"));
        assertEquals(FallbackMode.DEFAULT, FallbackMode.parse("teleport"));
        assertEquals(FallbackMode.DEFAULT, FallbackMode.parse(null));
    }

    @Test
    @DisplayName("the fallback mode cycles through every value and wraps")
    void fallbackModeCycles() {
        assertEquals(FallbackMode.MOVE, FallbackMode.OFF.next());
        assertEquals(FallbackMode.MOVE_OR_DROP, FallbackMode.MOVE.next());
        assertEquals(FallbackMode.OFF, FallbackMode.MOVE_OR_DROP.next());
    }

    @Test
    @DisplayName("v1 -> v2 mask migration still decodes the low bits")
    void legacyMaskMigrationIsIntact() {
        // 0b100000101 -> slots 0, 2 and 8.
        List<SlotBlock> blocked = ConfigSchema.decodeLegacyMask(0b100000101, SlotBlock.HOTBAR_SLOT_COUNT);

        assertEquals(List.of(new SlotBlock(0, true), new SlotBlock(2, true), new SlotBlock(8, true)),
                blocked, "the mask bits must map to ascending slot ids");
        assertTrue(ConfigSchema.decodeLegacyMask(1 << 20, SlotBlock.HOTBAR_SLOT_COUNT).isEmpty(),
                "bits above the hotbar range must be ignored");
    }
}
