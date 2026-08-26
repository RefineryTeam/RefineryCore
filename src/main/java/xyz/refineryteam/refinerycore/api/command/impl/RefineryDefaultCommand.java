package xyz.refineryteam.refinerycore.api.command.impl;

import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.CommandContext;
import xyz.refineryteam.refinerycore.api.command.RefineryCommand;
import xyz.refineryteam.refinerycore.api.command.annotation.Command;
import xyz.refineryteam.refinerycore.api.command.annotation.DefaultHandler;
import xyz.refineryteam.refinerycore.api.command.annotation.Subcommand;
import xyz.refineryteam.refinerycore.api.config.RefineryConfigurationRegistry;
import xyz.refineryteam.refinerycore.plugin.RefineryCorePlugin;

import java.util.concurrent.atomic.AtomicInteger;

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
        context.executiveContext(plugin::reloadPlugin)
                .onSuccess(() -> context.replyRefineryPrefix("<green>Reloaded successfully."))
                .onFailure(e -> {
                    context.replyRefineryPrefix("<red>Something went wrong.");
                    plugin.getLogger().severe("Reload failed: " + e.getMessage());
                });
    }

    @Subcommand(
            value = "reloadPlugins",
            description = "Reload's the plugins configuration that is registered to the configuration registry",
            permission = "refinerycore.commands.reload"
    )
    public void onReloadPlugins(@NonNull CommandContext context) {
        AtomicInteger reloaded = new AtomicInteger();
        context.executiveContext(() -> reloaded.set(RefineryConfigurationRegistry.getInstance().reload().size()))
                .onSuccess(() -> context.replyRefineryPrefix("<green>Reloaded %s sub-plugins successfully!".formatted(reloaded.get())))
                .onFailure(e -> {
                    context.replyRefineryPrefix("<red>Something went wrong, check console>");
                    plugin.getCrashHandler().report(e);
                });
    }

}
