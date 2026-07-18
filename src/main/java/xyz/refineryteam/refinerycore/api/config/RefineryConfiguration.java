package xyz.refineryteam.refinerycore.api.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.config.annotation.Category;
import xyz.refineryteam.refinerycore.api.config.annotation.ConfigEntry;
import xyz.refineryteam.refinerycore.api.config.annotation.ConfigFile;
import xyz.refineryteam.refinerycore.api.config.annotation.ConfigSection;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Level;

public abstract class RefineryConfiguration {

    private File file;
    private FileConfiguration yaml;
    private final JavaPlugin plugin;

    protected RefineryConfiguration(@NonNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveDefault() {
        ConfigFile meta = resolveFileMeta();
        this.file = new File(plugin.getDataFolder(), meta.value());

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create config file: " + file.getName(), e);
                return;
            }
        }

        this.yaml = YamlConfiguration.loadConfiguration(file);
        writeDefaults(this, null);

        save();
    }

    public void load() {
        if (file == null || yaml == null) saveDefault();
        this.yaml = YamlConfiguration.loadConfiguration(file);
        readInto(this, null);
    }

    public void reload() {
        load();
    }

    public void save() {
        if (yaml == null || file == null) return;
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save config: " + file.getName(), e);
        }
    }

    private void writeDefaults(@NonNull Object instance, String sectionPrefix) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(ConfigEntry.class)) {
                ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
                String path = buildPath(sectionPrefix, entry.key());

                if (!yaml.contains(path)) {
                    try {
                        Object value = field.get(instance);
                        if (value instanceof java.util.List<?> list) {
                            yaml.set(path, new java.util.ArrayList<>(list));
                        } else {
                            yaml.set(path, value);
                        }
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
                    if (nested != null) writeDefaults(nested, nestedPrefix);
                } catch (IllegalAccessException e) {
                    plugin.getLogger().warning("Could not access section field: " + field.getName());
                }
            }
        }
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
                            field.set(instance, value instanceof java.util.HashMap<?, ?> map
                                    ? new java.util.HashMap<>(map)
                                    : new java.util.HashMap<>());
                        } else {
                            field.set(instance, castValue(field.getType(), value));
                        }
                    } catch (IllegalAccessException e) {
                        plugin.getLogger().warning("Could not set field for path: " + path);
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

    private Object castValue(Class<?> type, Object value) {
        if (value == null) return null;
        if (type == int.class || type == Integer.class) return ((Number) value).intValue();
        if (type == long.class || type == Long.class) return ((Number) value).longValue();
        if (type == double.class || type == Double.class) return ((Number) value).doubleValue();
        if (type == float.class || type == Float.class) return ((Number) value).floatValue();
        if (type == boolean.class || type == Boolean.class) return value;
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

    public FileConfiguration getYaml() {
        return yaml;
    }
}