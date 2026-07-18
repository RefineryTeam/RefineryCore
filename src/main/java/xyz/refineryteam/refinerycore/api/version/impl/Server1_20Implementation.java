package xyz.refineryteam.refinerycore.api.version.impl;

import org.jetbrains.annotations.NotNull;
import xyz.refineryteam.refinerycore.api.version.AbstractServerImplementation;
import xyz.refineryteam.refinerycore.api.version.Feature;

/**
 * Covers the 1.20.x line (1.20.0 through the latest 1.20 patch).
 * <p>
 * Notable differences from {@link Server1_21Implementation}:
 * <ul>
 *   <li>The Bogged mob does not exist — it was added in 1.21.</li>
 *   <li>Enchantments are not yet data-driven registry entries — the legacy static
 *       {@code Enchantment} constants are the only way to reference them.</li>
 * </ul>
 */
public class Server1_20Implementation extends AbstractServerImplementation {

    public Server1_20Implementation(String minecraftVersion) {
        super(minecraftVersion);
    }

    @Override
    public boolean supports(@NotNull Feature feature) {
        return switch (feature) {
            case DATA_DRIVEN_ENCHANTMENTS, BOGGED_MOB, WOLF_ARMOR -> false;
            case COPPER_OXIDATION_ITEMS -> true;
        };
    }
}
