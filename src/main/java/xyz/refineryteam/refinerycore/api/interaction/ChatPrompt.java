package xyz.refineryteam.refinerycore.api.interaction;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.interaction.internal.PromptRegistry;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fluent chat-based input request, so plugins stop hand-rolling a
 * {@code Map<UUID, Consumer<String>>} plus an {@code AsyncPlayerChatEvent}
 * listener every time they need a text answer (ban reason, custom amount,
 * search term, etc.).
 * <p>
 * The next chat message the player sends is captured and never broadcast
 * to chat. Call {@link PromptRegistry#install(Plugin)} once during your
 * plugin's {@code onEnable()} before using this.
 * <p>
 * Usage:
 * <pre>{@code
 * ChatPrompt.of(plugin, player)
 *     .prompt("<gray>Type a reason for the ban, or 'cancel' to abort:")
 *     .timeoutSeconds(30)
 *     .onInput(reason -> banPlayer(target, reason))
 *     .onCancel(() -> player.sendMessage(EasyMiniMessage.format("<red>Cancelled.")))
 *     .onTimeout(() -> player.sendMessage(EasyMiniMessage.format("<red>Timed out.")))
 *     .send();
 * }</pre>
 */
public final class ChatPrompt {

    private final Plugin plugin;
    private final Player player;

    private String promptMessage;
    private String cancelKeyword = "cancel";
    private long timeoutTicks = 20L * 30;
    private Consumer<String> onInput;
    private Runnable onCancel;
    private Runnable onTimeout;

    private ChatPrompt(Plugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public static @NonNull ChatPrompt of(@NonNull Plugin plugin, @NonNull Player player) {
        return new ChatPrompt(plugin, player);
    }

    public @NonNull ChatPrompt prompt(@NonNull String miniMessage) {
        this.promptMessage = miniMessage;
        return this;
    }

    /**
     * The word a player can type to back out instead of answering.
     * Defaults to "cancel". Pass {@code null} to disable this entirely.
     */
    public @NonNull ChatPrompt cancelKeyword(String keyword) {
        this.cancelKeyword = keyword;
        return this;
    }

    public @NonNull ChatPrompt timeoutSeconds(long seconds) {
        this.timeoutTicks = seconds * 20L;
        return this;
    }

    public @NonNull ChatPrompt noTimeout() {
        this.timeoutTicks = -1;
        return this;
    }

    public @NonNull ChatPrompt onInput(@NonNull Consumer<String> handler) {
        this.onInput = handler;
        return this;
    }

    public @NonNull ChatPrompt onCancel(@NonNull Runnable handler) {
        this.onCancel = handler;
        return this;
    }

    public @NonNull ChatPrompt onTimeout(@NonNull Runnable handler) {
        this.onTimeout = handler;
        return this;
    }

    /**
     * Registers the prompt and sends the prompt message. The next chat
     * message from this player is consumed by this prompt instead of
     * reaching chat.
     */
    public void send() {
        UUID id = player.getUniqueId();

        if (promptMessage != null) {
            player.sendMessage(EasyMiniMessage.format(promptMessage));
        }

        BukkitTask timeoutTask = null;
        if (timeoutTicks >= 0) {
            timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (PromptRegistry.cancel(id)) {
                    if (onTimeout != null) onTimeout.run();
                }
            }, timeoutTicks);
        }

        PromptRegistry.register(id, new PromptRegistry.PendingPrompt(
                cancelKeyword,
                timeoutTask,
                text -> {
                    if (onInput != null) onInput.accept(text);
                },
                () -> {
                    if (onCancel != null) onCancel.run();
                }
        ));
    }
}