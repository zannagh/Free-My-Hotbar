package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Validates the loader metadata shipped in the template exactly as Fabric / Forge would read
 * it: the JSON must parse and the TOML must parse, and each must carry the mod identity the two
 * loaders expect (dashed {@code free-my-hotbar} for Fabric/resources, underscored {@code free_my_hotbar}
 * for Forge). These invariants stay TRUE for any renamed mod once the setup script rewrites the
 * placeholders, because they assert structure plus the shipped placeholder values.
 */
@DisplayName("Mod metadata smoke")
class ModMetadataSmokeTest {

    /** Repo root, injected by the build; falls back to CWD so the suite is IDE-runnable. */
    private static final Path REPO_ROOT = Paths.get(
            System.getProperty("smoke.repo.root", System.getProperty("user.dir")));

    private static final String FABRIC_MOD_ID = "free-my-hotbar";
    private static final String FORGE_MOD_ID = "free_my_hotbar";
    private static final String BASE_PACKAGE = "io.github.zannagh.freemyhotbar";

    @Test
    @DisplayName("fabric.mod.json is valid JSON with the expected id and required keys")
    void fabricModJsonIsValid() throws IOException {
        JsonObject json = readJson("fabric/src/main/resources/fabric.mod.json");

        assertEquals(1, json.get("schemaVersion").getAsInt(), "schemaVersion must be 1");
        assertEquals(FABRIC_MOD_ID, json.get("id").getAsString(), "fabric.mod.json id must match the mod id");
        for (String key : new String[] {"version", "name", "entrypoints", "mixins", "depends"}) {
            assertTrue(json.has(key), "fabric.mod.json must declare '" + key + "'");
        }
        assertTrue(json.getAsJsonObject("entrypoints").has("main"),
                "fabric.mod.json must declare a 'main' entrypoint");
    }

    @Test
    @DisplayName("mods.toml parses, declares the expected modId, and uses classic-Forge dependencies")
    void forgeModsTomlIsValid() throws IOException {
        String content = readString("forge/src/main/resources/META-INF/mods.toml");
        TomlParseResult toml = Toml.parse(content);

        assertFalse(toml.hasErrors(), () -> "mods.toml must parse: " + toml.errors());
        assertEquals("javafml", toml.getString("modLoader"), "modLoader must be javafml");

        TomlArray mods = toml.getArray("mods");
        assertNotNull(mods, "mods.toml must declare at least one [[mods]] table");
        assertTrue(mods.size() >= 1, "mods.toml must declare at least one [[mods]] table");
        TomlTable firstMod = mods.getTable(0);
        assertEquals(FORGE_MOD_ID, firstMod.getString("modId"),
                "the Forge modId must be the underscored form of the mod id");

        // Classic Forge (1.20.1 / FML 47) declares dependency requiredness with the boolean field
        // `mandatory`. NeoForge (1.20.2+) replaced it with `type="required"`; using that here throws
        // at runtime ("Missing required field mandatory in dependency"). Guard against the NeoForge
        // syntax leaking back in, since it only surfaces when the client actually boots.
        TomlArray deps = toml.getArray("dependencies." + FORGE_MOD_ID);
        assertNotNull(deps, "mods.toml must declare [[dependencies." + FORGE_MOD_ID + "]] entries");
        boolean hasForgeDep = false;
        for (int i = 0; i < deps.size(); i++) {
            TomlTable dep = deps.getTable(i);
            String modId = dep.getString("modId");
            assertTrue(dep.contains("mandatory"),
                    () -> "Forge dependency '" + modId + "' must use the boolean `mandatory` field (classic Forge)");
            assertFalse(dep.contains("type"),
                    () -> "Forge dependency '" + modId + "' must not use NeoForge's `type` field; use `mandatory`");
            if ("forge".equals(modId)) {
                hasForgeDep = true;
                assertEquals(Boolean.TRUE, dep.getBoolean("mandatory"),
                        "the forge dependency must be mandatory=true");
            }
        }
        assertTrue(hasForgeDep, "mods.toml must declare a dependency on modId=\"forge\"");
    }

    @Test
    @DisplayName("common mixin config is valid JSON with the expected package")
    void commonMixinsJsonIsValid() throws IOException {
        JsonObject json = readJson("common/src/main/resources/free-my-hotbar.mixins.json");

        assertEquals(BASE_PACKAGE + ".mixin", json.get("package").getAsString(),
                "the common mixin package must sit under the mod's base package");
        assertTrue(json.has("mixins"), "the mixin config must declare a 'mixins' array");
    }

    @Test
    @DisplayName("client mixin config is valid JSON with the expected package")
    void clientMixinsJsonIsValid() throws IOException {
        JsonObject json = readJson("common/src/client/resources/free-my-hotbar.client.mixins.json");

        assertEquals(BASE_PACKAGE + ".mixin.client", json.get("package").getAsString(),
                "the client mixin package must sit under the mod's base mixin package");
        assertTrue(json.has("client"), "the client mixin config must declare a 'client' array");
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        return JsonParser.parseString(readString(relativePath)).getAsJsonObject();
    }

    private static String readString(String relativePath) throws IOException {
        Path file = REPO_ROOT.resolve(relativePath);
        assertTrue(Files.exists(file), () -> "expected template file is missing: " + file);
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
