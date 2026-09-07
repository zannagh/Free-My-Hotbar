package io.github.zannagh.freemyhotbar.forge.client;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
import io.github.zannagh.freemyhotbar.forge.net.ForgeNetworking;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-only Forge glue. Registers the keybind (mod bus), opens the lock screen on the key press
 * (forge tick), performs common client init and installs the mask sync sender (client setup), and
 * resends the mask when the player joins a world. Loaded only on the physical client via the
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
        MinecraftForge.EVENT_BUS.addListener(FreeMyHotbarClientForge::onLoggingIn);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FreeMyHotbarKeys.OPEN_SCREEN);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        FreeMyHotbarClient.init();
        FreeMyHotbarClient.setSyncSender(ForgeNetworking::sendMask);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FreeMyHotbarKeys.handleTick(Minecraft.getInstance());
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ForgeNetworking.sendMask(FreeMyHotbarClient.config().mask());
    }
}
