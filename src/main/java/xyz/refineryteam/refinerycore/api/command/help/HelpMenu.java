package xyz.refineryteam.refinerycore.api.command.help;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.RefineryCommand;
import xyz.refineryteam.refinerycore.api.command.annotation.Command;
import xyz.refineryteam.refinerycore.api.command.annotation.HelpInfo;
import xyz.refineryteam.refinerycore.api.command.annotation.Subcommand;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HelpMenu {

    private static final TextColor ACCENT     = TextColor.color(0xA78BFA);
    private static final TextColor DIM        = TextColor.color(0x6B7280);
    private static final TextColor ENTRY_CMD  = TextColor.color(0xE2E8F0);
    private static final TextColor ENTRY_ARGS = TextColor.color(0x94A3B8);
    private static final TextColor HOVER_TITLE = TextColor.color(0xC4B5FD);
    private static final TextColor HOVER_BODY  = TextColor.color(0xD1D5DB);
    private static final TextColor SEPARATOR   = TextColor.color(0x374151);

    private HelpMenu() {}

    public static void send(@NonNull CommandSender sender, @NonNull RefineryCommand command) {
        Command meta = command.getClass().getAnnotation(Command.class);
        if (meta == null) return;

        HelpInfo info = command.getClass().getAnnotation(HelpInfo.class);

        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        lines.add(buildHeader(meta, info));
        lines.add(buildSeparator());

        collectEntries(command, meta).stream()
            .sorted(Comparator.comparing(HelpEntry::subcommand))
            .forEach(entry -> lines.add(buildEntry(meta.name(), entry)));

        lines.add(buildSeparator());

        if (info != null && !info.footer().isEmpty()) {
            lines.add(
                Component.text(info.footer())
                    .color(DIM)
                    .decoration(TextDecoration.ITALIC, true)
            );
        }

        lines.add(Component.empty());
        lines.forEach(sender::sendMessage);
    }

    private static @NonNull Component buildHeader(@NonNull Command meta, HelpInfo info) {
        String headerText = (info != null && !info.header().isEmpty())
            ? info.header()
            : capitalize(meta.name()) + " commands";

        TextComponent.Builder builder = Component.text()
            .append(Component.text("  ").color(ACCENT))
            .append(Component.text(headerText).color(ENTRY_CMD).decorate(TextDecoration.BOLD));

        if (!meta.description().isEmpty()) {
            builder.append(Component.text("  —  ").color(DIM))
                   .append(Component.text(meta.description()).color(DIM));
        }

        return builder.build();
    }

    private static @NonNull Component buildSeparator() {
        return Component.text("  " + "─".repeat(40)).color(SEPARATOR);
    }

    private static @NonNull Component buildEntry(String rootName, @NonNull HelpEntry entry) {
        String usage = "/" + rootName + (entry.subcommand().isEmpty() ? "" : " " + entry.subcommand());
        String argsHint = entry.completions().isEmpty() ? "" : " " + entry.completions();

        Component hoverContent = Component.text()
            .append(Component.text(usage).color(HOVER_TITLE).decorate(TextDecoration.BOLD))
            .appendNewline()
            .append(
                entry.description().isEmpty()
                    ? Component.text("No description provided.").color(DIM).decorate(TextDecoration.ITALIC)
                    : Component.text(entry.description()).color(HOVER_BODY)
            )
            .append(
                entry.permission().isEmpty()
                    ? Component.empty()
                    : Component.text()
                        .appendNewline()
                        .append(Component.text("Permission: ").color(DIM))
                        .append(Component.text(entry.permission()).color(HOVER_TITLE))
                        .build()
            )
            .build();

        return Component.text()
            .append(Component.text("  "))
            .append(Component.text("▸ ").color(ACCENT))
            .append(
                Component.text(usage).color(ENTRY_CMD)
                    .hoverEvent(HoverEvent.showText(hoverContent))
                    .clickEvent(ClickEvent.suggestCommand(usage + " "))
            )
            .append(Component.text(argsHint).color(ENTRY_ARGS))
            .build();
    }

    private static @NonNull List<HelpEntry> collectEntries(@NonNull RefineryCommand command, Command meta) {
        List<HelpEntry> entries = new ArrayList<>();

        for (Method method : command.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Subcommand.class)) continue;

            Subcommand sub = method.getAnnotation(Subcommand.class);
            String completionsHint = String.join(" ", sub.completions());

            entries.add(new HelpEntry(
                sub.value(),
                sub.description(),
                sub.permission(),
                completionsHint
            ));
        }

        return entries;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private record HelpEntry(
        String subcommand,
        String description,
        String permission,
        String completions
    ) {}
}