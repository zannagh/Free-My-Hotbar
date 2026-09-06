package smoke;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.examplemod.ExampleMod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Loader-agnostic smoke checks for the mod's core entrypoint. Compiled against :common's main
 * source (ExampleMod only) with no Minecraft, no Loom, and no client bootstrap - so these run in
 * milliseconds and still catch a template whose core class no longer loads or initialises.
 */
@DisplayName("ExampleMod core smoke")
class ExampleModCoreSmokeTest {

    @Test
    @DisplayName("MOD_ID matches the shipped identity")
    void modIdMatchesShippedIdentity() {
        assertEquals("example-mod", ExampleMod.MOD_ID,
                "MOD_ID must match the fabric.mod.json id so both halves of the template stay in sync");
    }

    @Test
    @DisplayName("a logger is available")
    void loggerIsAvailable() {
        assertNotNull(ExampleMod.LOGGER, "ExampleMod.LOGGER must be initialised");
    }

    @Test
    @DisplayName("init() runs without throwing")
    void initDoesNotThrow() {
        assertDoesNotThrow(ExampleMod::init, "ExampleMod.init() must complete on a bare JVM");
    }
}
