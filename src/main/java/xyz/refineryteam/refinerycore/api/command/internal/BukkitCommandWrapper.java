package xyz.refineryteam.refinerycore.api.command.internal;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

public final class BukkitCommandWrapper extends Command {

    private final CommandExecutorBridge bridge;

    public BukkitCommandWrapper(String name, String description, String[] aliases, CommandExecutorBridge bridge) {
        super(name);
        this.setDescription(description);
        this.setAliases(Arrays.asList(aliases));
        this.bridge = bridge;
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        return bridge.onCommand(sender, this, label, args);
    }

    @Override
    public @NonNull List<String> tabComplete(@NonNull CommandSender sender, @NonNull String alias, @NonNull String[] args) {
        return bridge.onTabComplete(sender, this, alias, args);
    }
}