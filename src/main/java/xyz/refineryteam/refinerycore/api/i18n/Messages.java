package xyz.refineryteam.refinerycore.api.i18n;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player-locale message registry backed by YAML files in
 * {@code <datafolder>/lang/<locale>.yml}. Falls back to a default locale,
 * then to the key itself, so missing translations never crash.
 * <p>
 * File format ({@code lang/en_us.yml}):
 * <pre>{@code
 * prefix: "<gradient:#A78BFA:#7F77DD>MyPlugin</gradient> <dark_gray>»</dark_gray> "
 * errors:
 *   no-permission: "<red>You can't do that."
 *   not-found: "<red>Player <white>%target%</white> wasn't found."
 * }</pre>
 * Usage:
 * <pre>{@code
 * Messages messages = Messages.of(plugin, Locale.US);
 * messages.send(player, "errors.no-permission");
 * messages.send(player, "errors.not-found",
 *     Placeholder.unparsed("target", name));
 * }</pre>
 * Player locales are resolved from the client's language setting; override
 * per player with {@link #setLocale(java.util.UUID, Locale)}.
 */
public final class Messages {

    private final Plugin plugin;
    private final String folderName;
    private volatile Locale defaultLocale = Locale.US;
    private final Map<String, YamlConfiguration> bundles = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Locale> overrides = new ConcurrentHashMap<>();

    private Messages(org.bukkit.plugin.Plugin plugin, @NonNull String folderName) {
        this.plugin = plugin;
        this.folderName = folderName;
    }

    /**
     * Creates a registry reading from {@code lang/} inside the plugin's
     * data folder (the conventional location).
     *
     * @param plugin the plugin whose data folder holds the lang files
     * @return a new empty registry; call {@link #load()} to read the files
     */
    public static @NonNull Messages of(@NonNull Plugin plugin) {
        return new Messages(plugin, "lang");
    }

    /**
     * Creates a registry reading from a custom sub-folder.
     *
     * @param plugin the plugin whose data folder holds the lang files
     * @param folder sub-folder name relative to the data folder,
     *               e.g. {@code "translations"}
     * @return a new empty registry; call {@link #load()} to read the files
     */
    public static @NonNull Messages of(@NonNull Plugin plugin, @NonNull String folder) {
        return new Messages(plugin, folder);
    }

    /**
     * Loads (or reloads) every {@code .yml} file in the lang folder.
     * Missing files are left alone — you ship defaults via
     * {@code saveResource("lang/en_us.yml", false)} before calling this.
     */
    public void load() {
        File dir = new File(plugin.getDataFolder(), folderName);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        Map<String, YamlConfiguration> loaded = new HashMap<>();
        for (File file : files) {
            String localeCode = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            loaded.put(localeCode, YamlConfiguration.loadConfiguration(file));
        }
        bundles.clear();
        bundles.putAll(loaded);
    }

    /**
     * Sets the fallback locale used when a player's locale has no bundle.
     *
     * @param locale the default locale (must match a loaded bundle code)
     */
    public void setDefaultLocale(@NonNull Locale locale) {
        this.defaultLocale = locale;
    }

    /**
     * Overrides the locale for one player (e.g. from /language command).
     *
     * @param playerId the player whose locale is overridden
     * @param locale   the locale to use for this player
     */
    public void setLocale(@NonNull UUID playerId, @NonNull Locale locale) {
        overrides.put(playerId, locale);
    }

    /**
     * Removes a player's locale override; the client-reported locale is
     * used again.
     *
     * @param playerId the player whose override is removed
     */
    public void clearLocale(@NonNull UUID playerId) {
        overrides.remove(playerId);
    }

    /**
     * @return the effective locale for the player: explicit override, then
     * the client-reported locale, then the default.
     */
    public @NonNull Locale localeFor(@Nullable Player player) {
        if (player == null) return defaultLocale;
        Locale override = overrides.get(player.getUniqueId());
        if (override != null) return override;
        String code = player.locale().getLanguage().toLowerCase(Locale.ROOT); // e.g. "en"
        // Try full match first ("en_us"), then language-only ("en").
        for (String candidate : List.of(code + "_" + player.locale().getCountry().toLowerCase(Locale.ROOT), code)) {
            if (bundles.containsKey(candidate)) return Locale.forLanguageTag(candidate.replace('_', '-'));
        }
        return defaultLocale;
    }

    /**
     * Resolves and formats a message as MiniMessage. Resolution order:
     * player's bundle → default bundle → raw key. A {@code prefix} entry
     * is prepended when using the send-family with prefix enabled.
     *
     * @param player    the viewing player (selects the bundle), or null for default
     * @param key       dotted config path, e.g. {@code "errors.no-permission"}
     * @param resolvers MiniMessage placeholder resolvers
     * @return the formatted component
     */
    public @NonNull Component component(@Nullable Player player, @NonNull String key, @NonNull TagResolver... resolvers) {
        String raw = resolveRaw(player, key);
        return EasyMiniMessage.format(raw, resolvers);
    }

    /**
     * Sends a message to an audience. For players the per-locale bundle is
     * used; console gets the default locale.
     *
     * @param audience  who receives the message; null is ignored
     * @param key       dotted config path of the message
     * @param resolvers MiniMessage placeholder resolvers
     */
    public void send(@Nullable Audience audience, @NonNull String key, @NonNull TagResolver... resolvers) {
        if (audience == null) return;
        Player player = audience instanceof Player p ? p : null;
        audience.sendMessage(component(player, key, resolvers));
    }

    /**
     * Sends a message prefixed with the bundle's {@code prefix} entry
     * (if present).
     *
     * @param audience  who receives the message; null is ignored
     * @param key       dotted config path of the message
     * @param resolvers MiniMessage placeholder resolvers
     */
    public void sendPrefixed(@Nullable Audience audience, @NonNull String key, @NonNull TagResolver... resolvers) {
        if (audience == null) return;
        Player player = audience instanceof Player p ? p : null;
        String prefix = resolveIn(bundleFor(player), "prefix");
        String body = resolveRaw(player, key);
        audience.sendMessage(EasyMiniMessage.format(
                (prefix != null ? prefix : "") + body, resolvers));
    }

    /**
     * Resolves a raw string without MiniMessage parsing — for places that
     * need plain text (scoreboard lines, Discord, etc).
     *
     * @param player the viewing player (selects the bundle), or null for default
     * @param key    dotted config path of the message
     * @return the untranslated-format string; the key itself if not found
     */
    public @NonNull String raw(@Nullable Player player, @NonNull String key) {
        return resolveRaw(player, key);
    }

    /**
     * @return true if the given key exists in any loaded bundle.
     */
    public boolean exists(@NonNull String key) {
        for (YamlConfiguration bundle : bundles.values()) {
            if (bundle.contains(key)) return true;
        }
        return false;
    }

    /**
     * @return all loaded locale codes (e.g. ["en_us", "de_de"]).
     */
    public @NonNull Set<String> availableLocales() {
        return Set.copyOf(bundles.keySet());
    }

    private @NonNull String resolveRaw(@Nullable Player player, @NonNull String key) {
        YamlConfiguration bundle = bundleFor(player);
        String value = resolveIn(bundle, key);
        if (value != null) return value;

        // Fall back to the default locale's bundle.
        YamlConfiguration fallback = bundles.get(localeCode(defaultLocale));
        if (fallback != null && fallback != bundle) {
            value = resolveIn(fallback, key);
            if (value != null) return value;
        }
        return key; // last resort: show the key so gaps are obvious
    }

    private @Nullable String resolveIn(YamlConfiguration bundle, @NonNull String path) {
        if (bundle == null || !bundle.contains(path)) return null;
        Object value = bundle.get(path);
        if (value instanceof List<?> list) {
            StringBuilder joined = new StringBuilder();
            for (Object item : list) {
                if (joined.length() > 0) joined.append('\n');
                joined.append(item);
            }
            return joined.toString();
        }
        return String.valueOf(value);
    }

    private YamlConfiguration bundleFor(@Nullable Player player) {
        Locale locale = localeFor(player);
        YamlConfiguration bundle = bundles.get(localeCode(locale));
        if (bundle != null) return bundle;
        return bundles.get(localeCode(defaultLocale));
    }

    private static @NonNull String localeCode(@NonNull Locale locale) {
        String tag = locale.toLanguageTag().toLowerCase(Locale.ROOT).replace('-', '_');
        return tag;
    }
}
