package io.github.zannagh.freemyhotbar;

import java.util.List;

/** Supplies the side-agnostic mixin set of {@code free-my-hotbar.mixins.json}. */
public final class CommonMixinPlugin extends FreeMyHotbarMixinPlugin {

    @Override
    public String getPackage() {
        return "io.github.zannagh.freemyhotbar.mixin";
    }

    @Override
    public List<String> getExpectedMixins() {
        return List.of("InventoryMixin");
    }
}
