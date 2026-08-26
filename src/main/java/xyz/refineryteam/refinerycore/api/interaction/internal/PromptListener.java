package xyz.refineryteam.refinerycore.api.interaction.internal;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public final class PromptListener implements Listener {

    private final Plugin plugin;

    public PromptListener(@NonNull Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NonNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        PromptRegistry.PendingPrompt prompt = PromptRegistry.take(player.getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Chat events fire on an async thread (and on region threads under Folia).
        // Consumer callbacks routinely touch Bukkit API, so hop to the player's
        // region thread before invoking them.
        ScheduledTask task = player.getScheduler().run(plugin, t -> {
            if (prompt.cancelKeyword() != null && message.equalsIgnoreCase(prompt.cancelKeyword())) {
                prompt.onCancel().run();
                return;
            }
            prompt.onInput().accept(message);
        }, null);
        if (task == null) {
            // Player retired between chat and scheduling — drop the prompt.
            prompt.onCancel().run();
        }
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        // Silent cleanup — no cancel callback, since there's no one left
        // to notify, and firing it could touch player-dependent state
        // (inventory, messages) on an offline player.
        PromptRegistry.take(event.getPlayer().getUniqueId());
    }
}