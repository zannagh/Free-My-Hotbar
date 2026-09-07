package io.github.zannagh.freemyhotbar.fabric;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared networking identifiers for the Fabric wiring. The client sends its 9-bit hotbar lock mask
 * to the server as a single varint on this channel; the server stores it per-player. Kept in the
 * main source set so both the client and server entrypoints reference the same id.
 */
public final class FreeMyHotbarChannels {

    /** Channel carrying the client's locked-slot mask (a single varint) to the server. */
    public static final ResourceLocation LOCKED_SLOTS =
            new ResourceLocation("free-my-hotbar", "locked_slots");

    private FreeMyHotbarChannels() {
    }
}
