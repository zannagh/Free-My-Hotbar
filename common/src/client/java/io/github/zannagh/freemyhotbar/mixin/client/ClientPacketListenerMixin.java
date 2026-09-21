package io.github.zannagh.freemyhotbar.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.zannagh.freemyhotbar.client.HotbarEvictor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;

/**
 * Tells the client-side fallback when the local player picked an item up.
 *
 * <p>This is the only reliable pickup signal a client-only install gets: 1.20.1 has no client
 * prediction for pickup, so nothing but this packet distinguishes "the server just put an item in
 * my hotbar" from "I moved it there myself". Injected at TAIL rather than HEAD because the
 * vanilla method starts with {@code PacketUtils.ensureRunningOnSameThread}, so only the tail runs
 * on the client thread.
 *
 * <p>The packet is also sent for experience orbs, which never touch the inventory. Those are
 * filtered out so XP pickup does not arm the evictor. The lookup is safe at TAIL: vanilla removes
 * a fully-collected {@code ItemEntity} from the level by then (so a null entity still means a real
 * item pickup) but deliberately leaves an {@code ExperienceOrb} in place.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleTakeItemEntity", at = @At("TAIL"))
    private void freeMyHotbar$onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        if (freeMyHotbar$isExperienceOrb(packet.getItemId())) {
            return;
        }
        HotbarEvictor.onItemPickedUp(packet.getPlayerId());
    }

    private static boolean freeMyHotbar$isExperienceOrb(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft != null ? minecraft.level : null;
        if (level == null) {
            return false;
        }
        Entity entity = level.getEntity(entityId);
        return entity instanceof ExperienceOrb;
    }
}
