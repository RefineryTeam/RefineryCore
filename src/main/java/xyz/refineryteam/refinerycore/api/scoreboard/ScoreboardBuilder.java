package xyz.refineryteam.refinerycore.api.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class ScoreboardBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Player player;
    private Component title = Component.text("");
    private final List<Component> lines = new ArrayList<>();

    ScoreboardBuilder(@NonNull Player player) {
        this.player = player;
    }

    public @NonNull ScoreboardBuilder title(@NonNull String miniMessage) {
        this.title = MM.deserialize(miniMessage);
        return this;
    }

    public @NonNull ScoreboardBuilder title(@NonNull Component component) {
        this.title = component;
        return this;
    }

    public @NonNull ScoreboardBuilder line(@NonNull String miniMessage) {
        this.lines.add(MM.deserialize(miniMessage));
        return this;
    }

    public @NonNull ScoreboardBuilder line(@NonNull Component component) {
        this.lines.add(component);
        return this;
    }

    public @NonNull ScoreboardBuilder blank() {
        return line("");
    }

    public @NonNull Scoreboard build() {
        Scoreboard board = new Scoreboard(player, title);
        if (!lines.isEmpty()) {
            board.linesComponent(lines);
        }
        return board;
    }
}