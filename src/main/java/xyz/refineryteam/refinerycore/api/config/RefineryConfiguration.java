package xyz.refineryteam.refinerycore.api.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.config.annotation.ConfigEntry;
import xyz.refineryteam.refinerycore.api.config.annotation.ConfigFile;
import xyz.refineryteam.refinerycore.api.config.annotation.ConfigSection;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Base class for annotation-driven, reflection-backed configuration files.
 * <p>
 * <b>Comment preservation.</b> If the plugin jar ships a resource at the
 * same path as {@link ConfigFile#value()} (e.g. {@code resources/config.yml}),
 * that resource is treated as the authoritative, hand-commented template:
 * on first run it is copied to disk byte-for-byte via
 * {@link JavaPlugin#saveResource(String, boolean)}, so every comment in the
 * source file survives untouched. On every load afterward, any entry the
 * template didn't define (e.g., added in a newer plugin version) is appended
 * to the bottom of the file as plain, uncommented lines rather than
 * triggering a full YAML re-serialization — {@link YamlConfiguration#save}
 * would otherwise silently discard every comment in the file. If no bundled
 * resource exists for this file, an empty file is created and populated
 * from field defaults with no comments, as before.
 */
public abstract class RefineryConfiguration {

    @Getter
    private File file;
    @Getter
    private FileConfiguration yaml;
    private final JavaPlugin plugin;

    protected RefineryConfiguration(@NonNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveDefault() {
        ConfigFile meta = resolveFileMeta();
        this.file = new File(plugin.getDataFolder(), meta.value());

        if (!file.exists()) {
            if (hasBundledResource(meta.value())) {
                // Copies the resource verbatim, preserving every comment
                // written in the source file — no reflection writes touch
                // this file's bytes on first run.
                plugin.saveResource(meta.value(), false);
            } else {
                file.getParentFile().mkdirs();
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to create config file: " + file.getName(), e);
                    return;
                }
            }
        }

        this.yaml = YamlConfiguration.loadConfiguration(file);

        // Only entries genuinely absent from the file (missing from an
        // outdated template, or there was no bundled resource at all) get
        // written. They're appended as plain text so existing comments and
        // formatting are left completely alone.
        appendMissingDefaults(this, null);
    }

    public void load() {
        if (file == null || yaml == null) saveDefault();
        this.yaml = YamlConfiguration.loadConfiguration(file);
        readInto(this, null);
    }

    public void reload() {
        load();
    }

    /**
     * Fully re-serializes the file with Bukkit's YAML writer. This is the
     * only path in this class that can lose handwritten comments — Bukkit's
     * {@link YamlConfiguration} has no concept of comments once a file is
     * parsed, so any comment present before a {@code save()} call will not
     * appear in the result. Prefer letting {@link #saveDefault()} /
     * {@link #load()} manage the file on disk; call this directly only when
     * a value was changed in code (e.g. from a {@code /config set} command)
     * and must be persisted.
     */
    public void save() {
        if (yaml == null || file == null) return;
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save config: " + file.getName(), e);
        }
    }

    private boolean hasBundledResource(@NonNull String resourcePath) {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            return stream != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Same traversal as the old {@code writeDefaults}, but instead of
     * mutating the in-memory {@link FileConfiguration} and re-saving the
     * whole document, it collects only the paths that are missing and
     * appends them to the file as raw YAML lines — a comment-safe
     * alternative to {@link YamlConfiguration#save}.
     */
    private void appendMissingDefaults(@NonNull Object instance, String sectionPrefix) {
        StringBuilder appendix = new StringBuilder();
        collectMissingDefaults(instance, sectionPrefix, appendix);

        if (appendix.isEmpty()) return;

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, true)) {
            writer.write("\n");
            writer.write(appendix.toString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to append missing defaults to: " + file.getName(), e);
            return;
        }

        // Re-parse so in-memory state reflects what's now on disk.
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    private void collectMissingDefaults(@NonNull Object instance, String sectionPrefix, @NonNull StringBuilder appendix) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(ConfigEntry.class)) {
                ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
                String path = buildPath(sectionPrefix, entry.key());

                if (!yaml.contains(path)) {
                    try {
                        Object value = field.get(instance);
                        appendYamlLine(appendix, path, value);
                    } catch (IllegalAccessException e) {
                        plugin.getLogger().warning("Could not read default for: " + path);
                    }
                }
            }

            ConfigSection section = field.getType().getAnnotation(ConfigSection.class);
            if (section != null) {
                String nestedPrefix = buildPath(sectionPrefix, section.value());
                try {
                    Object nested = field.get(instance);
                    if (nested != null) collectMissingDefaults(nested, nestedPrefix, appendix);
                } catch (IllegalAccessException e) {
                    plugin.getLogger().warning("Could not access section field: " + field.getName());
                }
            }
        }
    }

    /**
     * Appends a single {@code dotted.path: value} entry using a throwaway
     * {@link YamlConfiguration} to get correct YAML scalar/list formatting
     * (quoting, list syntax) without hand-rolling a serializer, then copies
     * just that rendered line(s) onto the appendix. {@code saveToString()}
     * has no file-header banner (unlike {@code save(File)}), so its output
     * is safe to append as-is.
     */
    private void appendYamlLine(@NonNull StringBuilder appendix, @NonNull String path, Object value) {
        YamlConfiguration scratch = new YamlConfiguration();
        if (value instanceof java.util.List<?> list) {
            scratch.set(path, new java.util.ArrayList<>(list));
        } else {
            scratch.set(path, value);
        }
        appendix.append(scratch.saveToString());
    }

    private void readInto(@NonNull Object instance, String sectionPrefix) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(ConfigEntry.class)) {
                ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
                String path = buildPath(sectionPrefix, entry.key());

                if (yaml.contains(path)) {
                    try {
                        Object value = yaml.get(path);
                        if (field.getType() == java.util.List.class || field.getType() == java.util.ArrayList.class) {
                            field.set(instance, value instanceof java.util.List<?> list
                                    ? new java.util.ArrayList<>(list)
                                    : new java.util.ArrayList<>());
                        } else if (field.getType() == java.util.Map.class || field.getType() == java.util.HashMap.class) {
                            field.set(instance, value instanceof java.util.Map<?, ?> map
                                    ? new java.util.HashMap<>(map)
                                    : new java.util.HashMap<>());
                        } else {
                            field.set(instance, castValue(path, field.getType(), value));
                        }
                    } catch (IllegalAccessException e) {
                        plugin.getLogger().warning("Could not set field for path: " + path);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Incompatible value for '" + path + "': " + e.getMessage()
                                + " — keeping the current default.");
                    }
                }
            }

            ConfigSection section = field.getType().getAnnotation(ConfigSection.class);
            if (section != null) {
                String nestedPrefix = buildPath(sectionPrefix, section.value());
                try {
                    Object nested = field.get(instance);
                    if (nested != null) readInto(nested, nestedPrefix);
                } catch (IllegalAccessException e) {
                    plugin.getLogger().warning("Could not access section field: " + field.getName());
                }
            }
        }
    }

    /**
     * Converts a raw YAML value to the field's type. Throws
     * {@link IllegalArgumentException} (not ClassCastException) with a
     * path-aware message so bad config values are diagnosable.
     */
    private Object castValue(String path, Class<?> type, Object value) {
        if (value == null) return null;
        try {
            if (type == int.class || type == Integer.class) return ((Number) value).intValue();
            if (type == long.class || type == Long.class) return ((Number) value).longValue();
            if (type == double.class || type == Double.class) return ((Number) value).doubleValue();
            if (type == float.class || type == Float.class) return ((Number) value).floatValue();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("expected a number but found '" + value + "' (" + value.getClass().getSimpleName() + ")");
        }
        if (type == boolean.class || type == Boolean.class) {
            if (value instanceof Boolean b) return b;
            throw new IllegalArgumentException("expected true/false but found '" + value + "'");
        }
        if (type == String.class) return value.toString();
        return value;
    }

    private String buildPath(String prefix, String key) {
        return (prefix == null || prefix.isEmpty()) ? key : prefix + "." + key;
    }

    private ConfigFile resolveFileMeta() {
        ConfigFile meta = getClass().getAnnotation(ConfigFile.class);
        if (meta == null) throw new IllegalStateException(
            getClass().getSimpleName() + " is missing @ConfigFile annotation."
        );
        return meta;
    }

}