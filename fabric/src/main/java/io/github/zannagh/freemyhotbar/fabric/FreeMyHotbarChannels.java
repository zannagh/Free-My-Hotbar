package io.github.zannagh.freemyhotbar.fabric;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared networking identifiers for the Fabric wiring. The client sends its blocked slots to the
 * server on this channel (a count followed by a (varint slotId, boolean blocked) pair per slot);
 * the server stores them per-player. Kept in the main source set so both the client and server
 * entrypoints reference the same id.
 */
public final class FreeMyHotbarChannels {

    /** Channel carrying the client's blocked slots to the server. */
    public static final ResourceLocation LOCKED_SLOTS =
            new ResourceLocation("free-my-hotbar", "locked_slots");

    private FreeMyHotbarChannels() {
    }
}
