package io.github.zannagh.freemyhotbar.forge;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.FreeMyHotbarNetworking;
import io.github.zannagh.freemyhotbar.forge.client.FreeMyHotbarClientForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entrypoint for Free My Hotbar. Registers the loader-agnostic networking (which also covers
 * the server-side cleanup of a logged-out player's stored slots) and — client-side only — the
 * keybind, client init and tick glue.
 */
@Mod(FreeMyHotbar.FORGE_MOD_ID)
public class FreeMyHotbarForge {

    public FreeMyHotbarForge() {
        FreeMyHotbar.init();
        // The constructor runs on both physical sides, which is where the locked-slots handler has
        // to be registered — see FreeMyHotbarNetworking.
        FreeMyHotbarNetworking.init();

        // Client-only glue: the supplier is class-loaded only on the physical client, so no client
        // classes are touched on a dedicated server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> FreeMyHotbarClientForge::init);
    }
}
