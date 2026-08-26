package xyz.refineryteam.refinerycore.api.config;

import org.abdullahcxd.consumers.BooleanResultConsumer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.refineryteam.refinerycore.api.crash.CrashHandler;
import xyz.refineryteam.refinerycore.api.plugin.RefineryPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Refinery Configuration Registry is a system used by the plugin to reload
 * plugin configuration based on plugins or every configuration found in the plugins.
 * <p>
 * You can register a configuration using {@link RefineryConfigurationRegistry#register(Plugin, RefineryConfiguration)}
 * <pre>
 * {@code public void onEnable() {
 *     getConfigurationRegistry().register(this, myConfiguration);
 * }}
 * </pre>
 */
public final class RefineryConfigurationRegistry {

    private static final Logger logger = LoggerFactory.getLogger("Refinery Configuration Registry");
    private static RefineryConfigurationRegistry instance;

    public static RefineryConfigurationRegistry getInstance() {
        if (instance == null) instance = new RefineryConfigurationRegistry();
        return instance;
    }

    private Map<Plugin, List<RefineryConfiguration>> pluginConfigurations;

    public RefineryConfigurationRegistry() {
        this.pluginConfigurations = new HashMap<>();
    }

    public boolean register(Plugin plugin, RefineryConfiguration configuration) {
        if (pluginConfigurations == null) pluginConfigurations = new HashMap<>();
        return pluginConfigurations.computeIfAbsent(plugin, ignored -> new ArrayList<>())
                .add(configuration);
    }

    public @Nullable RefineryConfiguration unregister(Plugin plugin, File file) {
        RefineryConfiguration configuration = findConfiguration(plugin, cfg -> cfg.getFile().equals(file));
        if (configuration == null) return null;
        List<RefineryConfiguration> configurations = pluginConfigurations.computeIfAbsent(plugin, ignored -> new ArrayList<>());
        configurations.remove(configuration);
        return configuration;
    }

    public @Nullable RefineryConfiguration findConfiguration(Plugin plugin, BooleanResultConsumer<RefineryConfiguration> consumer) {
        List<RefineryConfiguration> configurations = pluginConfigurations.computeIfAbsent(plugin, ignored -> new ArrayList<>());

        for (RefineryConfiguration configuration : configurations)
            if (consumer.accept(configuration))
                return configuration;

        return null;
    }

    public @NonNull List<RefineryConfiguration> reload(Plugin plugin) {
        List<RefineryConfiguration> configurations = pluginConfigurations.computeIfAbsent(plugin, ignored -> new ArrayList<>());
        List<RefineryConfiguration> reloaded = new ArrayList<>();

        for (RefineryConfiguration configuration : configurations) {
            try {
                configuration.reload();
                reloaded.add(configuration);
            } catch (Exception e) {
                reportCrash(plugin, e);
            }
        }

        return reloaded;
    }

    public @NonNull List<RefineryConfiguration> reload() {

        List<RefineryConfiguration> reloaded = new ArrayList<>();

        for (Plugin plugin : pluginConfigurations.keySet())
            reloaded.addAll(reload(plugin));

        return reloaded;

    }

    private void reportCrash(Plugin plugin, Exception e) {
        if (plugin instanceof RefineryPlugin refineryPlugin)
            refineryPlugin.getCrashHandler().report(e);
        else {
            logger.error("An error occurred while reloading configurations for %s".formatted(plugin.getDescription().getName()), e);
        }
    }
}
