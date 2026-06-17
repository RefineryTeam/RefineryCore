package xyz.refineryteam.refinerycore.plugin.banner;

import org.bukkit.command.ConsoleCommandSender;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

public final class RefineryBanner {

    private static final String[] LINES = {
            "  _____  ______ ",
            " |  __ \\|  ____|",
            " | |__) | |__   ",
            " |  _  /|  __|  ",
            " | | \\ \\| |     ",
            " |_|  \\_\\_|     "
    };

    private RefineryBanner() {}

    public static void print(@NonNull ConsoleCommandSender sender, String version) {
        sender.sendMessage(EasyMiniMessage.format(" "));
        for (String line : LINES) {
            sender.sendMessage(EasyMiniMessage.format(
                "<gradient:#A78BFA:#7F77DD>" + escape(line) + "</gradient>"
            ));
        }
        sender.sendMessage(EasyMiniMessage.format(
            "  <dark_gray>" + "─".repeat(40) + "</dark_gray>"
        ));
        sender.sendMessage(EasyMiniMessage.format(
            "  <gray>Refinery <white>v" + version + " <gray>— Refinery Team"
        ));
        sender.sendMessage(EasyMiniMessage.format(" "));
    }

    private static @NonNull String escape(@NonNull String line) {
        return line.replace("<", "\\<").replace(">", "\\>");
    }
}