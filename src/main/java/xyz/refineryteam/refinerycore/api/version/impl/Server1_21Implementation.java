package xyz.refineryteam.refinerycore.api.version.impl;

import org.jetbrains.annotations.NotNull;
import xyz.refineryteam.refinerycore.api.version.AbstractServerImplementation;
import xyz.refineryteam.refinerycore.api.version.Feature;

/**
 * Covers the entire 1.21.x line (1.21.0 through the latest 1.21 patch). Since RefineryCore
 * and RefineryCombat only use the public Bukkit/Paper API — no NMS, no reflection — a single
 * implementation is enough for the whole minor version line. Split this into narrower
 * implementations (e.g., a {@code Server1_21_2Implementation}) only when a specific patch adds
 * or changes a capability the plugin needs to detect.
 */
public class Server1_21Implementation extends AbstractServerImplementation {

    public Server1_21Implementation( String minecraftVersion) {
        super(minecraftVersion);
    }

    @Override
    public boolean supports(@NotNull Feature feature) {
        return switch (feature) {
            case DATA_DRIVEN_ENCHANTMENTS, BOGGED_MOB, COPPER_OXIDATION_ITEMS -> true;
            // Wolf armor was added in 1.21.2 — only report true from that patch onward.
            case WOLF_ARMOR -> isAtLeast("1.21.2");
        };
    }
}
