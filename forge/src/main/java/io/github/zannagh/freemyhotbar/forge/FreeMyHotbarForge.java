package io.github.zannagh.freemyhotbar.forge;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.forge.client.FreeMyHotbarClientForge;
import io.github.zannagh.freemyhotbar.forge.net.ForgeNetworking;
import io.github.zannagh.freemyhotbar.slot.SlotLockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entrypoint for Free My Hotbar. Wires the optional networking channel, the server-side
 * lifecycle cleanup (drop a player's stored mask on logout), and — client-side only — the keybind,
 * client init and mask sync glue.
 */
@Mod(ForgeNetworking.MOD_ID)
public class FreeMyHotbarForge {

    public FreeMyHotbarForge() {
        FreeMyHotbar.init();
        ForgeNetworking.register();

        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);

        // Client-only glue: the supplier is class-loaded only on the physical client, so no client
        // classes are touched on a dedicated server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> FreeMyHotbarClientForge::init);
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SlotLockState.remove(event.getEntity().getUUID());
    }
}
