package xyz.refineryteam.refinerycore.api.crash;

import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Central exception reporting with human-readable help lines.
 * <p>
 * Every output line is written through the plugin's {@link Logger} (never
 * raw to stdout), so each line keeps its normal logging prefix — timestamp,
 * thread, and log level stay intact and the report reads like any other
 * server log output instead of a broken interleaved block.
 * <p>
 * Matching reports are also appended to
 * {@code logs/refinery-crash-reports.txt} for post-mortem reading, since
 * console spam scrolls away.
 * <p>
 * Usage:
 * <pre>{@code
 * CrashHandler crashes = CrashHandler.of(plugin);
 *
 * crashes.hint(CrashHint.of()
 *     .matchesType(java.sql.SQLException.class)
 *     .title("Database error")
 *     .context("SQL State", e -> e instanceof java.sql.SQLException sql ? sql.getSQLState() : null)
 *     .help(
 *         "1. Is the database reachable from this machine? Try ping/host.",
 *         "2. Check database.username / database.password in config.yml.",
 *         "3. If using SQLite, verify the data folder is writable."
 *     )
 *     .build());
 *
 * try {
 *     riskyOperation();
 * } catch (Exception e) {
 *     crashes.report(e, "while running riskyOperation");
 * }
 * }</pre>
 */
public final class CrashHandler {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final List<CrashHint> hints = new CopyOnWriteArrayList<>();
    private volatile boolean fileLogging = true;

    private CrashHandler(@NonNull Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a handler bound to the given plugin. Reports are logged
     * through the plugin's logger and (by default) appended to
     * {@code logs/refinery-crash-reports.txt}.
     *
     * @param plugin the owning plugin; its logger and data folder are used
     * @return a new handler with no hints registered
     */
    public static @NonNull CrashHandler of(@NonNull Plugin plugin) {
        return new CrashHandler(plugin);
    }

    /**
     * Registers a hint. First matching hint wins — register more specific
     * hints before general ones.
     *
     * @param hint the hint to add
     * @return this handler, for chaining
     */
    public @NonNull CrashHandler hint(@NonNull CrashHint hint) {
        hints.add(hint);
        return this;
    }

    /**
     * Disables writing reports to {@code logs/refinery-crash-reports.txt};
     * reports will only go to the console log.
     *
     * @return this handler, for chaining
     */
    public @NonNull CrashHandler withoutFileLogging() {
        this.fileLogging = false;
        return this;
    }

    /**
     * Reports an exception with no extra location description.
     *
     * @param throwable the exception to report; null is silently ignored
     */
    public void report(@Nullable Throwable throwable) {
        report(throwable, null);
    }

    /**
     * Reports an exception with a short description of what was happening,
     * e.g. {@code "while saving player data"}. Each report line is written
     * as its own logger call so the standard timestamp/level prefix is
     * preserved on every line.
     *
     * @param throwable the exception to report; null is silently ignored
     * @param where     short description of the failing operation, or null
     */
    public void report(@Nullable Throwable throwable, @Nullable String where) {
        if (throwable == null) return;

        List<String> lines = buildLines(throwable, where);

        // One logger call per line → each line carries the standard slf4j /
        // log4j prefix ([HH:mm:ss INFO]) and never breaks mid-line.
        Logger log = plugin.getLogger();
        for (String line : lines) {
            log.severe(line);
        }

        if (fileLogging) {
            appendToFile(lines, throwable);
        }
    }

    /**
     * Builds the full report lines: header box, context fields, hint help
     * bullets, and stack trace.
     */
    private @NonNull List<String> buildLines(@NonNull Throwable throwable, @Nullable String where) {
        List<String> lines = new ArrayList<>();

        Throwable root = rootCause(throwable);
        CrashHint hint = findHint(root);

        lines.add("");
        lines.add("╔══════════════════════════════════════════════════════════════╗");
        lines.add("║  ⚠ " + pad(hint != null ? hint.title() : "Unhandled exception", 58));
        lines.add("╚══════════════════════════════════════════════════════════════╝");

        if (where != null && !where.isBlank()) {
            lines.add("  Where:      " + where);
        }
        lines.add("  Exception:  " + throwable.getClass().getName());
        String message = throwable.getMessage();
        lines.add("  Message:    " + (message != null ? message : "<no message>"));
        if (root != throwable) {
            lines.add("  Root cause: " + root.getClass().getName());
            String rootMessage = root.getMessage();
            if (rootMessage != null && !rootMessage.equals(message)) {
                lines.add("              " + rootMessage);
            }
        }

        if (hint != null) {
            Map<String, String> context = hint.context(root);
            for (Map.Entry<String, String> entry : context.entrySet()) {
                lines.add("  " + pad(entry.getKey(), 12).substring(0, 11) + ": " + entry.getValue());
            }

            if (!hint.helpLines().isEmpty()) {
                lines.add("");
                lines.add("  ── What you can try ──");
                for (String helpLine : hint.helpLines()) {
                    lines.add("   • " + helpLine);
                }
            }
        }

        lines.add("");
        lines.add("  ── Stack trace ──");
        for (StackTraceElement element : throwable.getStackTrace()) {
            lines.add("    at " + element);
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            Throwable cause = throwable.getCause();
            while (cause != null) {
                lines.add("    Caused by: " + cause.getClass().getName()
                        + (cause.getMessage() != null ? ": " + cause.getMessage() : ""));
                for (StackTraceElement element : cause.getStackTrace()) {
                    lines.add("      at " + element);
                }
                cause = cause.getCause() != cause ? cause.getCause() : null;
            }
        }

        lines.add("");
        lines.add("  Report saved under logs/refinery-crash-reports.txt");
        lines.add("");

        return lines;
    }

    private @Nullable CrashHint findHint(@NonNull Throwable root) {
        for (CrashHint hint : hints) {
            if (hint.matches(root)) return hint;
        }
        return null;
    }

    private static @NonNull Throwable rootCause(@NonNull Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static @NonNull String pad(@NonNull String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        return text + " ".repeat(width - text.length());
    }

    private void appendToFile(@NonNull List<String> lines, @NonNull Throwable throwable) {
        File dir = new File(plugin.getDataFolder().getParentFile(), "logs");
        if (!dir.exists() && !dir.mkdirs()) return;
        File file = new File(dir, "refinery-crash-reports.txt");

        StringBuilder out = new StringBuilder();
        out.append("=== ").append(STAMP.format(Instant.now()))
           .append(" — ").append(throwable.getClass().getName()).append(" ===\n");
        for (String line : lines) {
            if (!line.isBlank()) out.append(line.stripTrailing()).append('\n');
        }
        out.append('\n');

        try {
            Files.writeString(file.toPath(), out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write crash report file: " + e.getMessage());
        }
    }
}
