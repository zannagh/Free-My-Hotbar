package io.github.zannagh.freemyhotbar.paper;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal PaperMC (Bukkit) plugin entry point for the template.
 *
 * <p>It touches no NMS and no version-specific API, so one jar loads on every game version the
 * mod supports. Replace this class with the server-side half of your mod.</p>
 */
public final class FreeMyHotbarPaper extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Free My Hotbar (Paper) enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("Free My Hotbar (Paper) disabled");
    }
}
