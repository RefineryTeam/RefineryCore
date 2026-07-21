package xyz.refineryteam.refinerycore.api.version;

/**
 * Capability flags for behavior that differs between Minecraft versions.
 * <p>
 * Callers should check {@link ServerImplementation#supports(Feature)} instead of
 * comparing {@link ServerImplementation#getMinecraftVersion()} directly — this keeps
 * version-branching logic in one place (the {@link ServerImplementation} tree) rather
 * than scattered across the plugin.
 * <p>
 * Add a constant here whenever a new plugin feature needs to behave differently on a
 * given server version, then implement the check in the relevant {@link ServerImplementation}.
 */
public enum Feature {

    /**
     * Whether {@code Registry.ENCHANTMENT} exposes the data-driven enchantment registry
     * (1.21+) rather than the legacy static {@code Enchantment} constants.
     */
    DATA_DRIVEN_ENCHANTMENTS,

    /**
     * Whether wolf armor items and the related equipment slot exist (added 1.21.2).
     */
    WOLF_ARMOR,

    /**
     * Whether the vanilla "copper" family of blocks/tools with oxidation states exists.
     */
    COPPER_OXIDATION_ITEMS,

    /**
     * Whether the Bogged mob and its associated equipment behavior exist (added 1.21).
     */
    BOGGED_MOB
}
