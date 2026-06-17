package xyz.refineryteam.refinerycore.api.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.refineryteam.refinerycore.api.command.CommandRegistry;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

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
}