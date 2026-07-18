package xyz.refineryteam.refinerycore.api.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.annotation.Command;
import xyz.refineryteam.refinerycore.api.command.help.HelpMenu;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

public abstract class RefineryCommand {

    public void onNoMatch(@NonNull CommandContext context) {
        Command meta = getClass().getAnnotation(Command.class);
        String name = meta != null ? meta.name() : "command";
        context.sender().sendMessage(
                EasyMiniMessage.format(
                        "<gray>Unknown subcommand. Use <white>/<cmd> help</white> for a list of commands.",
                        Placeholder.unparsed("cmd", name)
                )
        );
    }

    public void onPermissionDenied(@NonNull CommandContext context) {
        context.reply("<red>You don't have permission to do that.");
    }

    public void onPlayerOnly(@NonNull CommandContext context) {
        context.reply("<red>This command can only be used by players.");
    }
    
    public void onHelp(@NonNull CommandContext context) {
        HelpMenu.send(context.sender(), this);
    }
}