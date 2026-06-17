package xyz.refineryteam.refinerycore.api.command.impl;

import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.CommandContext;
import xyz.refineryteam.refinerycore.api.command.RefineryCommand;
import xyz.refineryteam.refinerycore.api.command.annotation.Command;
import xyz.refineryteam.refinerycore.api.command.annotation.DefaultHandler;
import xyz.refineryteam.refinerycore.api.command.annotation.Subcommand;
import xyz.refineryteam.refinerycore.plugin.RefineryCorePlugin;

@Command(
        name = "refinery",
        aliases = { "refinerycore" },
        description = "The main command for the RefineryCore plugin",
        permission = "refinerycore.admin"
)
public class RefineryDefaultCommand extends RefineryCommand {

    private final RefineryCorePlugin plugin;

    public RefineryDefaultCommand(RefineryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @DefaultHandler
    public void onDefault(@NonNull CommandContext ctx) {
        ctx.replyRefineryPrefix("<green>You are running <gold>RefineryCore</gold> version <gold>" + plugin.getPluginMeta().getVersion() + "</gold>.</green>");
    }

    @Subcommand(
            value = "reload",
            description = "Reloads the configurations of the core plugin",
            permission = "refinerycore.commands.reload"
    )
    public void onReload(@NonNull CommandContext context) {
        context.executiveContext(plugin::reload)
                .onSuccess(() -> context.replyRefineryPrefix("<green>Reloaded successfully."))
                .onFailure(e -> {
                    context.replyRefineryPrefix("<red>Something went wrong.");
                    plugin.getLogger().severe("Reload failed: " + e.getMessage());
                });
    }

}
