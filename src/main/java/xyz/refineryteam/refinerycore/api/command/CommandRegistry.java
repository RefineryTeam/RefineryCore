package xyz.refineryteam.refinerycore.api.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.annotation.Command;
import xyz.refineryteam.refinerycore.api.command.internal.BukkitCommandWrapper;
import xyz.refineryteam.refinerycore.api.command.internal.CommandExecutorBridge;

import java.lang.reflect.Field;

public final class CommandRegistry {

    private final Plugin plugin;

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

        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
            commandMap.register(plugin.getName().toLowerCase(), wrapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register command: " + annotation.name(), e);
        }
    }
}