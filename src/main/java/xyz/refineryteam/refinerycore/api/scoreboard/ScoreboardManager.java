package xyz.refineryteam.refinerycore.api.scoreboard;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks one {@link Scoreboard} per player and optionally runs a repeating
 * task that pushes updated content (for live stats, timers, etc).
 * <p>
 * Usage:
 * <pre>{@code
 * ScoreboardManager boards = new ScoreboardManager(plugin);
 *
 * boards.create(player, sb -> sb
 *     .title("<gradient:blue:aqua><b>MyServer")
 *     .lines(
 *         "",
 *         "<gray>Rank: <white>%rank%",
 *         "<gray>Coins: <gold>%coins%",
 *         ""
 *     ));
 *
 * boards.startUpdating(player, 20L, sb -> sb
 *     .line(1, "<gray>Rank: <white>" + getRank(player))
 *     .line(2, "<gray>Coins: <gold>" + getCoins(player)));
 * }</pre>
 */
public final class ScoreboardManager {

    private final Plugin plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> updaters = new ConcurrentHashMap<>();

    public ScoreboardManager(@NonNull Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates (or replaces) a scoreboard for the player using the builder
     * DSL, and shows it immediately.
     */
    public @NonNull Scoreboard create(@NonNull Player player, @NonNull Consumer<ScoreboardBuilder> configurer) {
        remove(player);
        ScoreboardBuilder builder = Scoreboard.builder(player);
        configurer.accept(builder);
        Scoreboard board = builder.build();
        boards.put(player.getUniqueId(), board);
        return board;
    }

    /**
     * Starts a repeating task that re-applies the given mutator every
     * {@code periodTicks}, letting you push dynamic content (timers,
     * placeholders, live stats) without manually juggling BukkitRunnable.
     */
    public void startUpdating(@NonNull Player player, long periodTicks, @NonNull Consumer<Scoreboard> updater) {
        stopUpdating(player);
        UUID id = player.getUniqueId();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Scoreboard board = boards.get(id);
            if (board == null || !player.isOnline()) {
                stopUpdating(player);
                return;
            }
            updater.accept(board);
        }, 0L, periodTicks);

        updaters.put(id, task);
    }

    public void stopUpdating(@NonNull Player player) {
        BukkitTask task = updaters.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public @Nullable Scoreboard get(@NonNull Player player) {
        return boards.get(player.getUniqueId());
    }

    public boolean has(@NonNull Player player) {
        return boards.containsKey(player.getUniqueId());
    }

    public void remove(@NonNull Player player) {
        stopUpdating(player);
        Scoreboard board = boards.remove(player.getUniqueId());
        if (board != null) board.destroy();
    }

    /**
     * Call from onDisable() to clean up every tracked board and task.
     */
    public void shutdown() {
        for (UUID id : updaters.keySet()) {
            BukkitTask task = updaters.remove(id);
            if (task != null) task.cancel();
        }
        for (Scoreboard board : boards.values()) {
            board.destroy();
        }
        boards.clear();
    }
}