package io.github.zannagh.freemyhotbar.config;

import java.util.ArrayList;
import java.util.List;

import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.configuration.ConfigurationItem;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import org.jspecify.annotations.Nullable;

/**
 * The on-disk document behind {@code config/free-my-hotbar.json}, as eunomia's
 * {@link ConfigurationItem} so that {@code FileConfigurationProvider} owns load, save and migration.
 *
 * <p>The field names ARE the JSON keys and must not be renamed: real installs carry v1 files
 * ({@code configVersion:1} plus an integer {@code mask}) and v2 files (an explicit {@code slots}
 * list, no fallback settings). {@link #migrateFrom} is the single place those become a v3 document,
 * and it delegates every rule to {@link ConfigSchema} so the migration stays unit-testable.
 *
 * <p>Implements {@link ConfigurationItem} directly rather than extending
 * {@code ConfigurationItemBase}: this is a whole document round-tripped by plain reflective Gson,
 * not a single wrapped value, which is the same shape eunomia's own {@code EunomiaConfig} uses.
 */
public class ClientConfigData implements ConfigurationItem<ClientConfigData> {

    /**
     * FMH's schema is a single integer, so it maps onto a semantic version as {@code <n>.0.0}. An
     * absent {@code configVersion} reads as {@code 0.0.0} and therefore migrates, which is correct:
     * a document without the marker predates every version that wrote one.
     */
    private static final int SCHEMA_MINOR = 0;

    /** The schema this document was written with; absent (0) in files that predate versioning. */
    public int configVersion;

    /** Schema v2+: the blocked slots, explicit. Null means "absent" — then {@link #mask} decides. */
    public @Nullable List<SlotBlock> slots;

    /**
     * Schema v3: what to do on a server without the mod; null means "absent". Stored as a String
     * rather than the enum so {@link FallbackMode#parse} owns the read: Gson's enum adapter is
     * case-sensitive and silently yields null for anything it does not recognise, which would turn a
     * hand-edited {@code "move_or_drop"} into the default without a word.
     */
    public @Nullable String fallbackMode;

    /** Schema v3: suppress own clicks into locked slots; boxed so a null means "absent". */
    public @Nullable Boolean blockGuiInteractions;

    /** Schema v3: bypass the eviction movement gate; boxed so a null means "absent". */
    public @Nullable Boolean evictImmediately;

    /** Legacy v1 field: boxed so a null means "absent". Read only during migration. */
    public @Nullable Integer mask;

    private transient boolean changed;

    /**
     * Creates an unstamped document. Gson instantiates through this constructor and only then
     * overwrites the keys the JSON actually contains, so {@link #configVersion} is deliberately left
     * at 0: a field initialiser would stamp every legacy document as current and the migration would
     * never run.
     */
    public ClientConfigData() {
    }

    /**
     * Builds a document already stamped with the current schema, so loading it needs no migration.
     *
     * <p>The slot list is normalised to the full explicit 0-8 enumeration, which is the shape every
     * v2 and v3 file on disk carries (and the shape the wire format uses), so a migrated file is
     * byte-for-byte the same kind of document as one this mod has always written.
     *
     * @param slots the blocked slots; null and non-blocked entries are treated as "not blocked".
     * @param fallbackMode the fallback behaviour; null falls back to {@link FallbackMode#DEFAULT}.
     * @param blockGuiInteractions whether own clicks into locked slots are suppressed.
     * @param evictImmediately whether the eviction movement gate is bypassed.
     * @return the stamped document.
     */
    public static ClientConfigData current(
            @Nullable List<SlotBlock> slots,
            @Nullable FallbackMode fallbackMode,
            boolean blockGuiInteractions,
            boolean evictImmediately) {
        ClientConfigData data = new ClientConfigData();
        data.configVersion = ConfigSchema.CURRENT_VERSION;
        data.slots = SlotBlock.fullList(
                SlotBlock.blockedOnly(slots == null ? List.of() : slots), SlotBlock.HOTBAR_SLOT_COUNT);
        data.fallbackMode = FallbackMode.orDefault(fallbackMode).name();
        data.blockGuiInteractions = blockGuiInteractions;
        data.evictImmediately = evictImmediately;
        return data;
    }

    /**
     * Builds the default document: no locked slots, every v3 setting at its {@link ConfigSchema}
     * default, already stamped with the current schema so loading it needs no migration.
     *
     * @return a fresh default document.
     */
    public static ClientConfigData defaults() {
        return current(List.of(), FallbackMode.DEFAULT,
                ConfigSchema.DEFAULT_BLOCK_GUI_INTERACTIONS, ConfigSchema.DEFAULT_EVICT_IMMEDIATELY);
    }

    /**
     * Returns the blocked slots this document describes, whichever schema wrote it: the explicit v2+
     * list when present, otherwise the decoded legacy v1 mask, otherwise nothing.
     *
     * @return the blocked slots, never null.
     */
    public List<SlotBlock> blockedSlots() {
        if (slots != null) {
            return new ArrayList<>(SlotBlock.blockedOnly(slots));
        }
        if (mask != null) {
            return ConfigSchema.decodeLegacyMask(mask, SlotBlock.HOTBAR_SLOT_COUNT);
        }
        return List.of();
    }

    /**
     * Returns the fallback behaviour, defaulting an absent or unparseable value.
     *
     * @return the mode, never null.
     */
    public FallbackMode fallbackMode() {
        return FallbackMode.parse(fallbackMode);
    }

    /**
     * Returns whether own clicks into locked slots are suppressed, defaulting an absent value.
     *
     * @return the effective setting.
     */
    public boolean blockGuiInteractions() {
        return ConfigSchema.blockGuiInteractionsOrDefault(blockGuiInteractions);
    }

    /**
     * Returns whether the eviction movement gate is bypassed, defaulting an absent value.
     *
     * @return the effective setting.
     */
    public boolean evictImmediately() {
        return ConfigSchema.evictImmediatelyOrDefault(evictImmediately);
    }

    @Override
    public ClientConfigData getValue() {
        return this;
    }

    @Override
    public void setValue(ClientConfigData newValue) {
        ClientConfigData source = newValue == null ? getDefaultValue() : newValue;
        configVersion = source.configVersion;
        slots = source.slots;
        fallbackMode = source.fallbackMode;
        blockGuiInteractions = source.blockGuiInteractions;
        evictImmediately = source.evictImmediately;
        mask = source.mask;
    }

    @Override
    public ClientConfigData getDefaultValue() {
        return defaults();
    }

    @Override
    public boolean hasChangedFromSerializedContent() {
        return changed;
    }

    @Override
    public void setHasChangedFromSerializedContent() {
        changed = true;
    }

    @Override
    public SemanticVersion getSchemaVersion() {
        return new SemanticVersion(configVersion, SCHEMA_MINOR, 0, null);
    }

    @Override
    public SemanticVersion getCurrentSchemaVersion() {
        return new SemanticVersion(ConfigSchema.CURRENT_VERSION, SCHEMA_MINOR, 0, null);
    }

    /**
     * Rewrites a v1 or v2 document in the current shape.
     *
     * <p>v1 stored the blocked slots as an integer {@code mask}; {@link #blockedSlots()} decodes it
     * when no explicit list is present, and the migrated document drops the field. v2 already carried
     * the list but none of the fallback settings, which take their {@link ConfigSchema} defaults.
     * Nothing here can widen a player's locks: a value that was on disk is carried over verbatim, and
     * only genuinely absent fields are defaulted.
     *
     * @param old the document as read from disk.
     * @return a current-schema document with the same meaning.
     */
    @Override
    public ClientConfigData migrateFrom(ClientConfigData old) {
        return current(old.blockedSlots(), old.fallbackMode(),
                old.blockGuiInteractions(), old.evictImmediately());
    }
}
