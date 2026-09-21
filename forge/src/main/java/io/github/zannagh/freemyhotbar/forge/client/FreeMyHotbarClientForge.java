package io.github.zannagh.freemyhotbar.forge.client;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-only Forge glue. Registers the keybind (mod bus) and performs common client init (client
 * setup), then forwards the forge client tick. Join and disconnect handling is loader-agnostic and
 * lives in {@link FreeMyHotbarClient}. Loaded only on the physical client via the
 * {@code DistExecutor} guard in the mod constructor.
 */
public final class FreeMyHotbarClientForge {

    private FreeMyHotbarClientForge() {
    }

    /** Registers this class's listeners on the mod and forge event buses. */
    public static void init() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(FreeMyHotbarClientForge::onRegisterKeyMappings);
        modBus.addListener(FreeMyHotbarClientForge::onClientSetup);

        MinecraftForge.EVENT_BUS.addListener(FreeMyHotbarClientForge::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FreeMyHotbarKeys.OPEN_SCREEN);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        FreeMyHotbarClient.init();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FreeMyHotbarClient.clientTick(Minecraft.getInstance());
        }
    }
}
