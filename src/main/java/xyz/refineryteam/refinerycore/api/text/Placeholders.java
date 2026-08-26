package xyz.refineryteam.refinerycore.api.text;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lightweight PlaceholderAPI-style resolver: plugins register named
 * placeholders once, then any string can reference them as
 * {@code %namespace_key%} or plain {@code %key%} and get substituted
 * before MiniMessage parsing.
 * <p>
 * Unlike PlaceholderAPI there is no external dependency — the registry
 * lives inside RefineryCore, so suite plugins share one namespace without
 * each shipping their own expansion.
 * <p>
 * Usage (provider):
 * <pre>{@code
 * Placeholders.register("economy", "balance", (player, args) ->
 *     String.valueOf(Economy.getBalance(player)));
 * }</pre>
 * Usage (consumer):
 * <pre>{@code
 * Component text = Placeholders.render("<gray>Balance: <gold>%economy_balance%", player);
 * }</pre>
 */
public final class Placeholders {

    private record Key(String namespace, String name) {}

    @FunctionalInterface
    public interface Resolver {
        /**
         * @param player the viewing player, or null for console/global contexts
         * @param args   anything after a colon in the placeholder body,
         *               e.g. {@code %myplugin_top:3%} → args = "3"; may be null
         * @return the replacement text; must not be null (return "" instead)
         */
        @NonNull String resolve(@Nullable Player player, @Nullable String args);
    }

    private static final Pattern PATTERN = Pattern.compile("%([a-zA-Z0-9_\\-]+)(?::([^%]*))?%");
    private static final Map<Key, Resolver> RESOLVERS = new ConcurrentHashMap<>();

    private Placeholders() {}

    /**
     * Registers a namespaced placeholder, e.g.
     * {@code register("economy", "balance", ...)} answers
     * {@code %economy_balance%}.
     *
     * @param namespace namespace prefix, lowercased internally
     * @param name      placeholder name within the namespace
     * @param resolver  produces the replacement value at render time
     */
    public static void register(@NonNull String namespace, @NonNull String name, @NonNull Resolver resolver) {
        RESOLVERS.put(new Key(namespace.toLowerCase(), name.toLowerCase()), resolver);
    }

    /**
     * Registers an unnamespaced placeholder answering {@code %name%}.
     * Prefer the namespaced form to avoid collisions between plugins.
     *
     * @param name     placeholder name, lowercased internally
     * @param resolver produces the replacement value at render time
     */
    public static void register(@NonNull String name, @NonNull Resolver resolver) {
        RESOLVERS.put(new Key("", name.toLowerCase()), resolver);
    }

    /**
     * Removes a previously registered placeholder.
     *
     * @param namespace the namespace it was registered under
     * @param name      the name it was registered under
     */
    public static void unregister(@NonNull String namespace, @NonNull String name) {
        RESOLVERS.remove(new Key(namespace.toLowerCase(), name.toLowerCase()));
    }

    /**
     * Removes a previously registered unnamespaced placeholder.
     *
     * @param name the name it was registered under
     */
    public static void unregister(@NonNull String name) {
        RESOLVERS.remove(new Key("", name.toLowerCase()));
    }

    /**
     * Substitutes all known placeholders in {@code raw}, then parses the
     * result as MiniMessage. Unknown placeholders are left verbatim so
     * other systems (or PlaceholderAPI itself) can still handle them.
     *
     * @param raw    template string containing {@code %placeholder%} tokens
     * @param player the viewing player passed to resolvers, or null for
     *               console/global contexts
     * @return the rendered component
     */
    public static net.kyori.adventure.text.@NonNull Component render(@NonNull String raw, @Nullable Player player) {
        return EasyMiniMessage.format(apply(raw, player));
    }

    /**
     * Substitutes all known placeholders and returns the raw string,
     * without MiniMessage parsing. Useful when the result feeds another
     * component builder or a non-text destination (scoreboard lines,
     * Discord webhooks, etc).
     *
     * @param raw    template string containing {@code %placeholder%} tokens
     * @param player the viewing player passed to resolvers, or null for
     *               console/global contexts
     * @return the substituted string
     */
    public static @NonNull String apply(@NonNull String raw, @Nullable Player player) {
        if (!raw.contains("%")) return raw;

        Matcher matcher = PATTERN.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String namespace = matcher.group(1).toLowerCase();
            String args = matcher.group(2);

            // "ns_name" is ambiguous — try exact namespace match first,
            // then fall back to treating the whole thing as a bare name.
            Resolver resolver = null;
            int underscore = namespace.indexOf('_');
            if (underscore > 0) {
                resolver = RESOLVERS.get(new Key(namespace.substring(0, underscore), namespace.substring(underscore + 1)));
            }
            if (resolver == null) {
                resolver = RESOLVERS.get(new Key("", namespace));
            }

            String replacement = matcher.group(0); // leave unknown placeholders untouched
            if (resolver != null) {
                try {
                    replacement = resolver.resolve(player, args);
                } catch (Exception e) {
                    replacement = "";
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * @return true if any registered placeholder matches in {@code raw}.
     */
    public static boolean hasPlaceholders(@NonNull String raw) {
        if (!raw.contains("%")) return false;
        Matcher matcher = PATTERN.matcher(raw);
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase();
            int underscore = token.indexOf('_');
            if (underscore > 0 && RESOLVERS.containsKey(new Key(token.substring(0, underscore), token.substring(underscore + 1)))) {
                return true;
            }
            if (RESOLVERS.containsKey(new Key("", token))) return true;
        }
        return false;
    }

    /**
     * Clears every registration. Intended for plugin disable/test teardown;
     * individual plugins should prefer {@link #unregister(String, String)}.
     */
    public static void clearAll() {
        RESOLVERS.clear();
    }
}
