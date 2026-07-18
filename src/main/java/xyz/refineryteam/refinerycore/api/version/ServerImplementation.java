package xyz.refineryteam.refinerycore.api.version;

/**
 * Abstraction over version-specific server behaviour.
 * <p>
 * RefineryCore and downstream plugins (RefineryCombat, etc.) should never branch on
 * {@code Bukkit.getMinecraftVersion()} or {@code Bukkit.getBukkitVersion()} directly.
 * Instead, ask the active {@link ServerImplementation} — obtained via
 * {@code RefineryCorePlugin.getInstance().getServerImplementation()} — whether a
 * {@link Feature} is supported, or delegate any version-sensitive construction
 * (item stacks, attributes, registry lookups) to it.
 * <p>
 * Only add a new implementation class when a version genuinely changes behaviour that
 * matters to the plugin (a renamed enum, a newly added registry entry, an API that isn't
 * present below a certain version). Most versions within a minor line (e.g. 1.21.0–1.21.11)
 * can share a single implementation.
 */
public interface ServerImplementation {

    /**
     * @return the raw Minecraft version string this server is running, e.g. {@code "1.21.4"}.
     * This should only be used for logging/diagnostics — prefer {@link #supports(Feature)}
     * for behavioural checks.
     */
    String getMinecraftVersion();

    /**
     * @return a comparable ordinal for this server's version, used internally to resolve
     * version ranges. Encoded as {@code major * 10_000 + minor * 100 + patch}
     * (e.g. 1.21.4 -> 12_10_04 -> encoded as 121004).
     */
    int getVersionOrdinal();

    /**
     * @param feature the capability to check
     * @return whether the running server supports the given feature
     */
    boolean supports(Feature feature);

    /**
     * @param other the version to compare against, e.g. {@code "1.21.4"}
     * @return true if this server's version is the same as or newer than {@code other}
     */
    default boolean isAtLeast(String other) {
        return getVersionOrdinal() >= ServerImplementations.encodeVersion(other);
    }
}
