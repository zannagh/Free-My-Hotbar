package com.example.examplemod.paper;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal PaperMC (Bukkit) plugin entry point for the template.
 *
 * <p>It touches no NMS and no version-specific API, so one jar loads on every game version the
 * mod supports. Replace this class with the server-side half of your mod.</p>
 */
public final class ExampleModPaper extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Example Mod (Paper) enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("Example Mod (Paper) disabled");
    }
}
