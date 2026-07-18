package xyz.refineryteam.refinerycore.api.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps a per-player Bukkit scoreboard, handling objective/team plumbing
 * so lines can be pushed as plain strings or Components without dealing
 * with 1.21's 40-line-width team-prefix/suffix limits manually.
 */
public final class Scoreboard {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MAX_LINES = 15;

    private final Player player;
    private final org.bukkit.scoreboard.Scoreboard bukkitBoard;
    private final Objective objective;
    private final Map<Integer, Line> lines = new LinkedHashMap<>();
    private Component title;

    Scoreboard(@NonNull Player player, @NonNull Component title) {
        this.player = player;
        this.title = title;
        this.bukkitBoard = player.getServer().getScoreboardManager().getNewScoreboard();
        this.objective = bukkitBoard.registerNewObjective(
                "sb-" + UUID.randomUUID().toString().substring(0, 8),
                Criteria.DUMMY,
                title
        );
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(bukkitBoard);
    }

    public static @NonNull ScoreboardBuilder builder(@NonNull Player player) {
        return new ScoreboardBuilder(player);
    }

    public @NonNull Scoreboard title(@NonNull String miniMessage) {
        return title(MM.deserialize(miniMessage));
    }

    public @NonNull Scoreboard title(@NonNull Component component) {
        this.title = component;
        this.objective.displayName(component);
        return this;
    }

    /**
     * Sets every line at once, top to bottom. Index 0 is the top line.
     * Accepts MiniMessage strings.
     */
    public @NonNull Scoreboard lines(@NonNull String @NonNull ... rawLines) {
        List<Component> parsed = new ArrayList<>(rawLines.length);
        for (String raw : rawLines) parsed.add(MM.deserialize(raw));
        return linesComponent(parsed);
    }

    public @NonNull Scoreboard lines(@NonNull List<String> rawLines) {
        return lines(rawLines.toArray(new String[0]));
    }

    public @NonNull Scoreboard linesComponent(@NonNull List<Component> components) {
        if (components.size() > MAX_LINES) {
            throw new IllegalArgumentException("Scoreboard supports at most " + MAX_LINES + " lines");
        }

        int size = components.size();

        for (Map.Entry<Integer, Line> entry : new ArrayList<>(lines.entrySet())) {
            if (entry.getKey() >= size) {
                removeLine(entry.getKey());
            }
        }

        for (int i = 0; i < size; i++) {
            setLine(i, components.get(i));
        }

        return this;
    }

    public @NonNull Scoreboard line(int index, @NonNull String miniMessage) {
        return setLine(index, MM.deserialize(miniMessage));
    }

    public @NonNull Scoreboard line(int index, @NonNull Component component) {
        return setLine(index, component);
    }

    private @NonNull Scoreboard setLine(int index, @NonNull Component component) {
        if (index < 0 || index >= MAX_LINES) {
            throw new IllegalArgumentException("Line index must be between 0 and " + (MAX_LINES - 1));
        }

        int score = MAX_LINES - index;
        Line line = lines.get(index);

        if (line == null) {
            String entryId = generateEntryId(index);
            Team team = bukkitBoard.registerNewTeam("sb-line-" + index);
            team.addEntry(entryId);
            team.prefix(component);
            objective.getScore(entryId).setScore(score);
            lines.put(index, new Line(team, entryId));
        } else {
            line.team().prefix(component);
        }

        return this;
    }

    public @NonNull Scoreboard removeLine(int index) {
        Line line = lines.remove(index);
        if (line != null) {
            bukkitBoard.resetScores(line.entryId());
            line.team().unregister();
        }
        return this;
    }

    public @NonNull Scoreboard clearLines() {
        for (Integer index : new ArrayList<>(lines.keySet())) {
            removeLine(index);
        }
        return this;
    }

    public @Nullable Component getLine(int index) {
        Line line = lines.get(index);
        if (line == null) return null;
        return line.team().prefix();
    }

    public int size() {
        return lines.size();
    }

    public @NonNull Player player() {
        return player;
    }

    /**
     * Restores the player's default scoreboard and unregisters everything
     * this instance owns. Always call this on quit/disable.
     */
    public void destroy() {
        clearLines();
        objective.unregister();
        if (player.isOnline()) {
            player.setScoreboard(player.getServer().getScoreboardManager().getMainScoreboard());
        }
    }

    private @NonNull String generateEntryId(int index) {
        // Legacy color codes are invisible and unique per index, giving us
        // stable per-line scoreboard "entries" without colliding with
        // actual player/team names.
        char[] colors = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            sb.append(org.bukkit.ChatColor.COLOR_CHAR).append(colors[n % colors.length]);
            n /= colors.length;
        }
        if (sb.isEmpty()) {
            sb.append(org.bukkit.ChatColor.COLOR_CHAR).append('0');
        }
        return sb.toString();
    }

    private record Line(Team team, String entryId) {
    }
}