package xyz.refineryteam.refinerycore.api.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.refineryteam.refinerycore.api.command.CommandRegistry;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;
import xyz.refineryteam.refinerycore.api.version.ServerImplementation;
import xyz.refineryteam.refinerycore.api.version.ServerImplementations;
import xyz.refineryteam.refinerycore.api.version.ServerSoftware;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public interface RefineryPluginImplementation {

    // Factory Abstraction Methods

    /**
     * Method used to reload data, this is for refinery plugins only.
     *
     * @deprecated Use {@link RefineryPlugin#reloadPlugin()} instead.
     */
    @Deprecated(since = "0.0.9")
    default void reload() {
        if (this instanceof JavaPlugin plugin)
            plugin.reloadConfig();
    }

    /**
     * Weakly-keyed so entries are dropped when a plugin instance is
     * garbage-collected (e.g., after /reload), preventing classloader leaks.
     * Wrapped in a synchronized view — WeakHashMap is not thread-safe.
     */
    Map<JavaPlugin, CommandRegistry> REGISTRIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    default ConsoleCommandSender getConsoleSender() {
        return Bukkit.getConsoleSender();
    }

    default void logMessage(String content) {
        getConsoleSender().sendMessage(EasyMiniMessage.format(content));
    }

    default PluginManager getPluginManager() {
        return Bukkit.getPluginManager();
    }

    /**
     * Returns the {@link CommandRegistry} for this plugin instance.
     * <p>
     * The map uses <em>weak</em> keys: once a plugin is disabled, reloaded,
     * and garbage-collected, its entry disappears automatically — no
     * classloader leak, unlike a plain static map keyed by {@code Class}.
     */
    default CommandRegistry getCommandRegistry() {
        if (!(this instanceof JavaPlugin javaPlugin)) {
            throw new IllegalStateException(getClass().getName()
                    + " must extend JavaPlugin to use getCommandRegistry().");
        }
        return REGISTRIES.computeIfAbsent(javaPlugin, CommandRegistry::new);
    }

    /**
     * @return the {@link ServerImplementation} matching the currently running server version.
     * Use this instead of checking {@code Bukkit.getVersion()} directly so version-specific
     * logic stays centralized in the {@code api.version} package.
     */
    default ServerImplementation getServerImplementation() {
        return ServerImplementations.current();
    }

    /**
     * @return the detected Bukkit-compatible server software
     */
    default ServerSoftware getServerSoftware() {
        return ServerSoftware.current();
    }
}