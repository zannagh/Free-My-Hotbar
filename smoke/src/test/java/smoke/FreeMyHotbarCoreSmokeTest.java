package smoke;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Loader-agnostic smoke checks for the mod's core entrypoint. Compiled against :common's main
 * source (FreeMyHotbar only) with no Minecraft, no Loom, and no client bootstrap - so these run in
 * milliseconds and still catch a template whose core class no longer loads or initialises.
 */
@DisplayName("FreeMyHotbar core smoke")
class FreeMyHotbarCoreSmokeTest {

    @Test
    @DisplayName("MOD_ID matches the shipped identity")
    void modIdMatchesShippedIdentity() {
        assertEquals("free-my-hotbar", FreeMyHotbar.MOD_ID,
                "MOD_ID must match the fabric.mod.json id so both halves of the template stay in sync");
    }

    @Test
    @DisplayName("a logger is available")
    void loggerIsAvailable() {
        assertNotNull(FreeMyHotbar.LOGGER, "FreeMyHotbar.LOGGER must be initialised");
    }

    @Test
    @DisplayName("init() runs without throwing")
    void initDoesNotThrow() {
        assertDoesNotThrow(FreeMyHotbar::init, "FreeMyHotbar.init() must complete on a bare JVM");
    }
}
