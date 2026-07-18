package xyz.refineryteam.refinerycore.api.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.refineryteam.refinerycore.api.command.CommandRegistry;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;
import xyz.refineryteam.refinerycore.api.version.ServerImplementation;
import xyz.refineryteam.refinerycore.api.version.ServerImplementations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface RefineryPluginImplementation {

    // Factory Abstraction Methods

    /**
     * Method used to reload data, this is for refinery plugins only.
     */
    default void reload() {
        if (this instanceof JavaPlugin plugin)
            plugin.reloadConfig();
    }

    Map<Class<?>, CommandRegistry> REGISTRIES = new ConcurrentHashMap<>();

    default ConsoleCommandSender getConsoleSender() {
        return Bukkit.getConsoleSender();
    }

    default void logMessage(String content) {
        getConsoleSender().sendMessage(EasyMiniMessage.format(content));
    }

    default PluginManager getPluginManager() {
        return Bukkit.getPluginManager();
    }

    default CommandRegistry getCommandRegistry() {
        return REGISTRIES.computeIfAbsent(getClass(), k -> new CommandRegistry((JavaPlugin) this));
    }

    /**
     * @return the {@link ServerImplementation} matching the currently running server version.
     * Use this instead of checking {@code Bukkit.getVersion()} directly so version-specific
     * logic stays centralized in the {@code api.version} package.
     */
    default ServerImplementation getServerImplementation() {
        return ServerImplementations.current();
    }
}