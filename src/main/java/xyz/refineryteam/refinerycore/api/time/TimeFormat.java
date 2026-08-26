package xyz.refineryteam.refinerycore.api.time;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human-friendly duration formatting and parsing — the "2h 15m" strings
 * every cooldown, ban, and timer UI needs, plus the inverse parse for
 * command arguments like {@code 7d12h}.
 * <p>
 * Usage:
 * <pre>{@code
 * TimeFormat.format(Duration.ofMinutes(135));        // "2h 15m"
 * TimeFormat.formatShort(Duration.ofSeconds(45));    // "45s"
 * TimeFormat.parse("1d6h30m");                       // Duration
 * }</pre>
 */
public final class TimeFormat {

    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long WEEK = DAY * 7;
    private static final long MONTH = DAY * 30;
    private static final long YEAR = DAY * 365;

    private static final Pattern PARSE_PATTERN =
            Pattern.compile("(\\d+)\\s*(y|mo|w|d|h|m|s|ms)", Pattern.CASE_INSENSITIVE);

    private TimeFormat() {}

    /**
     * Formats with up to two significant units: "1y 3mo", "2d 5h",
     * "4h 15m", "8m 30s", "45s", "0s".
     */
    public static @NonNull String format(@NonNull Duration duration) {
        return format(duration.toMillis(), 2);
    }

    /**
     * Formats with a single significant unit: "2h", "15m", "45s".
     */
    public static @NonNull String formatShort(@NonNull Duration duration) {
        return format(duration.toMillis(), 1);
    }

    /**
     * Full precision formatting with explicit unit count.
     */
    public static @NonNull String format(long millis, int maxUnits) {
        if (millis < 0) millis = 0;
        if (millis < 1000) return "0s";

        long years = millis / YEAR;      millis %= YEAR;
        long months = millis / MONTH;    millis %= MONTH;
        long weeks = millis / WEEK;      millis %= WEEK;
        long days = millis / DAY;        millis %= DAY;
        long hours = millis / HOUR;      millis %= HOUR;
        long minutes = millis / MINUTE;  millis %= MINUTE;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder out = new StringBuilder();
        int units = 0;
        units = appendUnit(out, years, "y", maxUnits, units);
        units = appendUnit(out, months, "mo", maxUnits, units);
        units = appendUnit(out, weeks, "w", maxUnits, units);
        units = appendUnit(out, days, "d", maxUnits, units);
        units = appendUnit(out, hours, "h", maxUnits, units);
        units = appendUnit(out, minutes, "m", maxUnits, units);
        appendUnit(out, seconds, "s", maxUnits, units);

        return !out.isEmpty() ? out.toString() : "0s";
    }

    /**
     * Appends one unit if we haven't hit {@code maxUnits} yet and the value
     * is non-zero (or a smaller unit after a larger one already started).
     * Returns the updated unit count.
     */
    private static int appendUnit(StringBuilder out, long value, @NonNull String suffix,
                                  int maxUnits, int unitsSoFar) {
        if (unitsSoFar >= maxUnits) return unitsSoFar;
        if (value == 0 && out.isEmpty()) return unitsSoFar; // skip leading zeros
        if (!out.isEmpty()) out.append(' ');
        out.append(value).append(suffix);
        return unitsSoFar + 1;
    }

    /**
     * Parses compact duration strings like {@code "90s"}, {@code "1d6h"},
     * {@code "2w"}, or plain seconds ({@code "300"} → 5 minutes).
     *
     * @return null when nothing in the input parses.
     */
    public static @Nullable Duration parse(@NonNull String input) {
        input = input.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) return null;

        // Bare number = seconds.
        if (input.matches("\\d+")) {
            return Duration.ofSeconds(Long.parseLong(input));
        }

        Matcher matcher = PARSE_PATTERN.matcher(input);
        long totalMillis = 0;
        int matches = 0;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            totalMillis += switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "y" -> value * YEAR;
                case "mo" -> value * MONTH;
                case "w" -> value * WEEK;
                case "d" -> value * DAY;
                case "h" -> value * HOUR;
                case "m" -> value * MINUTE;
                case "s" -> value * 1000L;
                case "ms" -> value;
                default -> 0L;
            };
            matches++;
        }
        if (matches == 0) return null;
        return Duration.ofMillis(totalMillis);
    }

    /**
     * Like {@link #parse(String)} but throws on invalid input — handy for
     * command argument validation where you want to report a specific error.
     */
    public static @NonNull Duration parseOrThrow(@NonNull String input) {
        Duration parsed = parse(input);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid duration: '" + input + "' (expected e.g. 1d6h30m)");
        }
        return parsed;
    }
}
