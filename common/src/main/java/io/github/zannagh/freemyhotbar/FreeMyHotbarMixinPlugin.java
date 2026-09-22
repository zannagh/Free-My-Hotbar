package io.github.zannagh.freemyhotbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Supplies a mixin config's class list at runtime, skipping every mixin whose class is not
 * actually on the classpath.
 *
 * <p>This is what makes a per-version mixin set safe. A mixin named in the JSON but missing from
 * the jar is a hard boot failure, not a warning - and across the version matrix a mixin can very
 * well be compiled out of one variant (its target class was renamed away, or the hook it needs
 * does not exist there). Listing mixins here instead of in the JSON turns "not compiled for this
 * version" into "not applied on this version".
 *
 * <p>The probe is a resource lookup, never {@code Class.forName}: loading a mixin class through
 * the normal class loader at plugin time would take it out of Mixin's hands entirely.
 */
public abstract class FreeMyHotbarMixinPlugin implements IMixinConfigPlugin {

    /**
     * Returns the package the config's mixins live in, without a trailing dot.
     *
     * @return the mixin package, matching the {@code package} field of the JSON config.
     */
    public abstract String getPackage();

    /**
     * Returns every mixin this config would like to apply, as package-relative class names.
     *
     * @return the candidate mixin names; the ones missing from the jar are dropped.
     */
    public abstract List<String> getExpectedMixins();

    /**
     * Returns whether this config's mixins target client-only classes.
     *
     * <p>Mixins handed back by {@link #getMixins()} land in the config's side-agnostic list, which
     * a dedicated server applies as readily as a client. A config that used to declare its mixins
     * under the JSON {@code "client"} key must therefore say so here, or its screen mixins would
     * be applied on a dedicated server whose jar has no screen classes at all.
     *
     * @return true when the mixins must only be applied on the client.
     */
    protected boolean isClientOnly() {
        return false;
    }

    @Override
    public List<String> getMixins() {
        if (isClientOnly() && MixinEnvironment.getDefaultEnvironment().getSide()
                != MixinEnvironment.Side.CLIENT) {
            return List.of();
        }
        List<String> present = new ArrayList<>();
        for (String mixin : getExpectedMixins()) {
            String resource = (getPackage() + "." + mixin).replace('.', '/') + ".class";
            if (getClass().getClassLoader().getResource(resource) != null) {
                present.add(mixin);
            } else {
                LoggerFactory.getLogger("Free My Hotbar - Mixin")
                        .debug("Skipping mixin absent from this version's jar: {}", mixin);
            }
        }
        return present;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
