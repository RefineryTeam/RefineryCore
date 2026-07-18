package xyz.refineryteam.refinerycore.api.interaction.internal;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NonNull;

public final class PromptListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NonNull AsyncChatEvent event) {
        PromptRegistry.PendingPrompt prompt = PromptRegistry.take(event.getPlayer().getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (prompt.cancelKeyword() != null && message.equalsIgnoreCase(prompt.cancelKeyword())) {
            prompt.onCancel().run();
            return;
        }

        prompt.onInput().accept(message);
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        // Silent cleanup — no cancel callback, since there's no one left
        // to notify, and firing it could touch player-dependent state
        // (inventory, messages) on an offline player.
        PromptRegistry.take(event.getPlayer().getUniqueId());
    }
}