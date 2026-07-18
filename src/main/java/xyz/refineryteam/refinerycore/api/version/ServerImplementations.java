package xyz.refineryteam.refinerycore.api.version;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import xyz.refineryteam.refinerycore.api.version.impl.Server1_20Implementation;
import xyz.refineryteam.refinerycore.api.version.impl.Server1_21Implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the {@link ServerImplementation} that matches the currently running server.
 * <p>
 * Implementations are registered against a minimum version they support (inclusive). At
 * resolution time, the highest-registered implementation whose minimum version is less than
 * or equal to the running server's version is selected — so newer patch releases
 * automatically fall through to the newest compatible implementation without needing a
 * new entry, until a genuine breaking change requires one.
 * <p>
 * To add support for a new version that changes behaviour:
 * <ol>
 *   <li>Create a new class extending {@link AbstractServerImplementation}</li>
 *   <li>Register it in the static block below with {@link #register(String, ServerImplementation)}</li>
 * </ol>
 */
public final class ServerImplementations {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private record Entry(int minOrdinal, ServerImplementation implementation) {}

    private static final List<Entry> REGISTERED = new ArrayList<>();
    private static ServerImplementation resolved;

    static {
        // 1.20.0 and every later 1.20.x patch.
        register("1.20.0", new Server1_20Implementation(runningVersion()));

        // 1.21.0 onward supersedes the 1.20 implementation above.
        register("1.21.0", new Server1_21Implementation(runningVersion()));

        // Example for the future, once a version actually needs different handling:
        // register("1.22.0", new Server1_22Implementation(runningVersion()));
    }

    private ServerImplementations() {}

    /**
     * Registers an implementation as the one to use starting from {@code minVersion}
     * (inclusive), until a later-registered implementation with a higher minimum version
     * takes over.
     */
    public static void register(String minVersion, ServerImplementation implementation) {
        REGISTERED.add(new Entry(encodeVersion(minVersion), implementation));
        REGISTERED.sort((a, b) -> Integer.compare(a.minOrdinal(), b.minOrdinal()));
        resolved = null; // force re-resolution
    }

    /**
     * @return the {@link ServerImplementation} matching the currently running server version.
     */
    public static ServerImplementation current() {
        if (resolved != null) return resolved;

        int runningOrdinal = encodeVersion(runningVersion());
        ServerImplementation best = null;
        for (Entry entry : REGISTERED) {
            if (entry.minOrdinal() <= runningOrdinal) {
                best = entry.implementation();
            }
        }

        if (best == null) {
            // Running on something older than anything we've registered for — fall back to
            // the oldest known implementation rather than throwing, so the plugin can at
            // least attempt to load (features will just report as unsupported where unsure).
            if (REGISTERED.isEmpty()) {
                throw new IllegalStateException("No ServerImplementation registered.");
            }
            best = REGISTERED.getFirst().implementation();
        }

        resolved = best;
        return resolved;
    }

    /**
     * @return the raw version string reported by the running server, e.g. {@code "1.21.4"}.
     */
    public static @NotNull String runningVersion() {
        // Bukkit.getMinecraftVersion() returns just "1.21.4" style strings on Paper.
        return Bukkit.getMinecraftVersion();
    }

    /**
     * Encodes a version string like {@code "1.21.4"} into a comparable integer ordinal.
     * Missing patch numbers default to 0 (e.g. {@code "1.21"} -> same ordinal as {@code "1.21.0"}).
     */
    public static int encodeVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unrecognized version string: " + version);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

        return (major * 10_000) + (minor * 100) + patch;
    }
}
