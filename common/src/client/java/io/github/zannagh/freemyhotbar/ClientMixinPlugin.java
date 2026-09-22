package io.github.zannagh.freemyhotbar;

import java.util.List;

/**
 * Supplies the client mixin set of {@code free-my-hotbar.client.mixins.json}.
 *
 * <p>Client-only: every one of these targets a screen or the client packet listener, none of which
 * a dedicated server has.
 */
public final class ClientMixinPlugin extends FreeMyHotbarMixinPlugin {

    @Override
    public String getPackage() {
        return "io.github.zannagh.freemyhotbar.mixin.client";
    }

    @Override
    public List<String> getExpectedMixins() {
        return List.of(
                "AbstractContainerScreenMixin",
                "ClientPacketListenerMixin",
                "CreativeModeInventoryScreenMixin",
                "CreativeSlotWrapperAccessor");
    }

    @Override
    protected boolean isClientOnly() {
        return true;
    }
}
