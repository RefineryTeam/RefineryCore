package xyz.refineryteam.refinerycore.api.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.annotation.Command;
import xyz.refineryteam.refinerycore.api.command.internal.BukkitCommandWrapper;
import xyz.refineryteam.refinerycore.api.command.internal.CommandExecutorBridge;

import java.util.ArrayList;
import java.util.List;

public final class CommandRegistry {

    private final Plugin plugin;
    private final List<BukkitCommandWrapper> registered = new ArrayList<>();

    public CommandRegistry(@NonNull Plugin plugin) {
        this.plugin = plugin;
    }

    public void register(@NonNull RefineryCommand command) {
        Class<?> clazz = command.getClass();
        if (!clazz.isAnnotationPresent(Command.class)) {
            throw new IllegalArgumentException(clazz.getSimpleName() + " is missing @Command annotation.");
        }

        Command annotation = clazz.getAnnotation(Command.class);
        CommandExecutorBridge bridge = new CommandExecutorBridge(command);

        BukkitCommandWrapper wrapper = new BukkitCommandWrapper(annotation.name(), annotation.description(), annotation.aliases(), bridge);

        if (!annotation.permission().isEmpty()) {
            wrapper.setPermission(annotation.permission());
        }

        // Paper exposes the command map directly — no reflection needed.
        CommandMap commandMap = Bukkit.getCommandMap();
        commandMap.register(plugin.getName().toLowerCase(), wrapper);
        registered.add(wrapper);
    }

    /**
     * Unregisters every command this registry added from the server's
     * command map. Call from {@code onDisable()} so {@code /reload} doesn't
     * leave stale wrappers pointing at a dead plugin instance.
     */
    public void unregisterAll() {
        CommandMap commandMap = Bukkit.getCommandMap();
        for (BukkitCommandWrapper wrapper : registered) {
            wrapper.unregister(commandMap);
        }
        registered.clear();
    }

    /**
     * @return all commands currently registered through this registry.
     */
    public @NonNull List<BukkitCommandWrapper> getRegisteredCommands() {
        return List.copyOf(registered);
    }
}