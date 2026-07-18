package xyz.refineryteam.refinerycore.api.interaction.internal;

import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks one pending {@code ChatPrompt} per player. Not meant to be used
 * directly — call {@link #install(Plugin)} once in your plugin's
 * {@code onEnable()} and use {@code ChatPrompt} for everything else.
 */
public final class PromptRegistry {

    private static final Map<UUID, PendingPrompt> PENDING = new ConcurrentHashMap<>();
    private static volatile boolean installed = false;

    private PromptRegistry() {
    }

    /**
     * Registers the shared listener that intercepts chat and quit events
     * for all {@code ChatPrompt}s. Safe to call multiple times (from
     * multiple plugins sharing this core) — only registers once per JVM.
     */
    public static synchronized void install(@NonNull Plugin plugin) {
        if (installed) return;
        plugin.getServer().getPluginManager().registerEvents(new PromptListener(), plugin);
        installed = true;
    }

    public static void register(@NonNull UUID playerId, @NonNull PendingPrompt prompt) {
        PendingPrompt previous = PENDING.put(playerId, prompt);
        if (previous != null) {
            cancelTimeoutTask(previous);
        }
    }

    /**
     * Removes and returns the pending prompt for a player, if any, without
     * invoking any callback. Used internally by the listener.
     */
    public static @Nullable PendingPrompt take(@NonNull UUID playerId) {
        PendingPrompt prompt = PENDING.remove(playerId);
        if (prompt != null) cancelTimeoutTask(prompt);
        return prompt;
    }

    /**
     * Cancels a pending prompt (e.g. on timeout) and runs its cancel
     * callback. Returns {@code false} if there was nothing pending,
     * meaning the player already answered before the timeout fired.
     */
    public static boolean cancel(@NonNull UUID playerId) {
        PendingPrompt prompt = take(playerId);
        if (prompt == null) return false;
        prompt.onCancel().run();
        return true;
    }

    private static void cancelTimeoutTask(PendingPrompt prompt) {
        if (prompt.timeoutTask() != null) {
            prompt.timeoutTask().cancel();
        }
    }

    public record PendingPrompt(
            @Nullable String cancelKeyword,
            @Nullable BukkitTask timeoutTask,
            @NonNull Consumer<String> onInput,
            @NonNull Runnable onCancel
    ) {
    }
}