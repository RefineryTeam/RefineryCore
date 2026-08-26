package xyz.refineryteam.refinerycore.api.crash;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One entry in a {@link CrashHandler}: a pattern that recognizes a class of
 * exception and the human guidance to print when it matches.
 * <p>
 * Usage:
 * <pre>{@code
 * CrashHint.of()
 *     .matches(cause -> cause instanceof SQLException)
 *     .title("Database error")
 *     .help(
 *         "Is the database server reachable from this machine?",
 *         "Check credentials in config.yml (database.username / database.password).",
 *         "If using SQLite, make sure the data folder isn't read-only."
 *     )
 *     .build();
 * }</pre>
 */
public final class CrashHint {

    private final Predicate<Throwable> matcher;
    private final String title;
    private final List<String> helpLines;
    private final Map<String, java.util.function.Function<Throwable, String>> extractors;

    private CrashHint(@NonNull Builder builder) {
        this.matcher = builder.matcher;
        this.title = builder.title;
        this.helpLines = List.copyOf(builder.helpLines);
        this.extractors = new LinkedHashMap<>(builder.contextExtractors);
    }

    /**
     * Starts building a new hint.
     *
     * @return a fresh builder with no matcher set — call
     *         {@link Builder#matches(Predicate)} or
     *         {@link Builder#matchesType(Class)} before {@link Builder#build()},
     *         otherwise the hint never matches anything.
     */
    public static @NonNull Builder of() {
        return new Builder();
    }

    /**
     * @return true if this hint applies to the given throwable.
     */
    public boolean matches(@NonNull Throwable throwable) {
        return matcher.test(throwable);
    }

    /**
     * @return the headline shown at the top of the report box,
     *         e.g. {@code "Database error"}.
     */
    public @NonNull String title() {
        return title;
    }

    /**
     * @return the human guidance lines rendered under
     *         "What you can try" when this hint matches.
     */
    public @NonNull List<String> helpLines() {
        return helpLines;
    }

    /**
     * @return extra key→value context lines extracted from the throwable
     * (e.g. "SQL State" → "42S02"), rendered after the title.
     */
    public @NonNull Map<String, String> context(@NonNull Throwable throwable) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.function.Function<Throwable, String>> entry : extractors.entrySet()) {
            try {
                String value = entry.getValue().apply(throwable);
                if (value != null && !value.isBlank()) {
                    out.put(entry.getKey(), value);
                }
            } catch (Exception ignored) {
                // A failing extractor must never break report generation.
            }
        }
        return out;
    }

    public static final class Builder {
        private Predicate<Throwable> matcher = t -> false;
        private String title = "Unhandled exception";
        private final List<String> helpLines = new ArrayList<>();
        private final Map<String, java.util.function.Function<Throwable, String>> contextExtractors = new LinkedHashMap<>();

        /**
         * Sets the predicate deciding whether this hint applies. Matching
         * against the root cause is done by the report generator — write
         * your predicate for the specific exception type you expect.
         *
         * @param matcher predicate tested against the root cause throwable
         * @return this builder
         */
        public @NonNull Builder matches(@NonNull Predicate<Throwable> matcher) {
            this.matcher = matcher;
            return this;
        }

        /**
         * Convenience: match by exact exception class (or subclass).
         *
         * @param type exception class to match against the root cause
         * @return this builder
         */
        public @NonNull Builder matchesType(@NonNull Class<? extends Throwable> type) {
            this.matcher = type::isInstance;
            return this;
        }

        /**
         * Sets the headline shown at the top of the report box.
         *
         * @param title short human-readable title, e.g. {@code "Database error"}
         * @return this builder
         */
        public @NonNull Builder title(@NonNull String title) {
            this.title = title;
            return this;
        }

        /**
         * Replaces all help lines with the given ones.
         *
         * @param lines the guidance lines shown under "What you can try";
         *              replaces any previously set lines
         * @return this builder
         */
        public @NonNull Builder help(@NonNull String... lines) {
            this.helpLines.clear();
            this.helpLines.addAll(Arrays.asList(lines));
            return this;
        }

        /**
         * Appends additional help lines without clearing existing ones.
         *
         * @param lines guidance lines to add after the current ones
         * @return this builder
         */
        public @NonNull Builder moreHelp(@NonNull String... lines) {
            this.helpLines.addAll(Arrays.asList(lines));
            return this;
        }

        /**
         * Adds a dynamic context line: {@code label} plus a value pulled
         * from the throwable at report time (e.g. SQL state, file path,
         * plugin name). Null/blank results are omitted.
         *
         * @param label     the field name shown in the report, e.g. {@code "SQL State"}
         * @param extractor extracts the value from the throwable; may return null
         * @return this builder
         */
        public @NonNull Builder context(@NonNull String label,
                                        java.util.function.Function<Throwable, String> extractor) {
            this.contextExtractors.put(label, extractor);
            return this;
        }

        /**
         * Creates the hint.
         *
         * @return an immutable {@link CrashHint} ready to register with
         *         {@link CrashHandler#hint(CrashHint)}
         */
        public @NonNull CrashHint build() {
            return new CrashHint(this);
        }
    }
}
