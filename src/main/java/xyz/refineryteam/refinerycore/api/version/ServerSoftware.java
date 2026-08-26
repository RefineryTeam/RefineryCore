package xyz.refineryteam.refinerycore.api.version;

import org.bukkit.Bukkit;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Identifies the Bukkit-compatible server software currently running.
 * <p>
 * Detection is based on the server name/version exposed by Bukkit and the
 * server implementation class name. Unknown forks
 * remain usable through {@link #reportedName()} and {@link #reportedVersion()}.
 */
public final class ServerSoftware {

    private final ServerSoftwareType type;
    private final String reportedName;
    private final String reportedVersion;

    private ServerSoftware(@NonNull ServerSoftwareType type, @NonNull String reportedName,
                           @NonNull String reportedVersion) {
        this.type = type;
        this.reportedName = reportedName;
        this.reportedVersion = reportedVersion;
    }

    /**
     * Detects the software from the currently running Bukkit server.
     *
     * @return a snapshot of the detected software and reported server metadata
     */
    public static @NonNull ServerSoftware current() {
        String name = safeServerName();
        String version = safeServerVersion();
        return new ServerSoftware(detectType(name), name, version);
    }

    /**
     * @return the detected software family
     */
    public @NonNull ServerSoftwareType type() {
        return type;
    }

    /**
     * @param expected software family to compare with
     * @return whether this server matches the expected family
     */
    public boolean is(@NonNull ServerSoftwareType expected) {
        return type == expected;
    }

    /**
     * @return the server name reported by Bukkit, or an empty string when unavailable
     */
    public @NonNull String reportedName() {
        return reportedName;
    }

    /**
     * @return the full server version string reported by Bukkit, or an empty string when unavailable
     */
    public @NonNull String reportedVersion() {
        return reportedVersion;
    }

    private static @NonNull ServerSoftwareType detectType(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("folia") || serverClassMentionsFolia()) return ServerSoftwareType.FOLIA;
        if (normalized.contains("purpur")) return ServerSoftwareType.PURPUR;
        if (normalized.contains("paper")) return ServerSoftwareType.PAPER;
        if (normalized.contains("spigot")) return ServerSoftwareType.SPIGOT;
        if (normalized.contains("craftbukkit")) return ServerSoftwareType.CRAFTBUKKIT;
        if (normalized.contains("bukkit")) return ServerSoftwareType.BUKKIT;
        return ServerSoftwareType.UNKNOWN;
    }

    private static boolean serverClassMentionsFolia() {
        try {
            return Bukkit.getServer().getClass().getName().toLowerCase(Locale.ROOT).contains("folia");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static @NonNull String safeServerName() {
        try {
            String name = Bukkit.getName();
            return name != null ? name : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static @NonNull String safeServerVersion() {
        try {
            String version = Bukkit.getVersion();
            return version != null ? version : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}