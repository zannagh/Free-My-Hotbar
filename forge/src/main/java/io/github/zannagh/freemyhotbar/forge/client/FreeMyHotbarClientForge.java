package io.github.zannagh.freemyhotbar.forge.client;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
import io.github.zannagh.freemyhotbar.client.HotbarEvictor;
import io.github.zannagh.freemyhotbar.client.ServerModNotice;
import io.github.zannagh.freemyhotbar.client.ServerModPresence;
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
 * (forge tick), performs common client init and installs the slot sync sender (client setup), and
 * resends the slots when the player joins a world. Joining and leaving also resolve and clear the
 * server-mod presence used by the client-side fallback. Loaded only on the physical client via the
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
        MinecraftForge.EVENT_BUS.addListener(FreeMyHotbarClientForge::onLoggingOut);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FreeMyHotbarKeys.OPEN_SCREEN);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        FreeMyHotbarClient.init();
        FreeMyHotbarClient.setSyncSender(ForgeNetworking::sendBlocks);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FreeMyHotbarKeys.handleTick(Minecraft.getInstance());
            ServerModNotice.tick(Minecraft.getInstance());
            HotbarEvictor.tick(Minecraft.getInstance());
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ServerModNotice.reset();
        ServerModPresence.set(resolvePresence(event));
        ForgeNetworking.sendBlocks(FreeMyHotbarClient.config().slots());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ServerModPresence.reset();
        ServerModNotice.reset();
        HotbarEvictor.reset();
    }

    /**
     * Resolves whether the remote side runs the mod. The integrated server always does, and Forge's
     * channel handshake completes before play starts, so this is reliable immediately - no grace
     * period is needed here.
     *
     * @param event the logging-in event carrying the connection.
     * @return the resolved presence state.
     */
    private static ServerModPresence.State resolvePresence(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.hasSingleplayerServer()) {
            return ServerModPresence.State.PRESENT;
        }
        if (event.getConnection() == null) {
            return ServerModPresence.State.UNKNOWN;
        }
        return ForgeNetworking.CHANNEL.isRemotePresent(event.getConnection())
                ? ServerModPresence.State.PRESENT
                : ServerModPresence.State.ABSENT;
    }
}
