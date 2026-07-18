package xyz.refineryteam.refinerycore.api.event;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Groups {@link RefineryBus} subscriptions made by a single plugin so they
 * can all be torn down in one call from {@code onDisable()}, instead of
 * leaking subscriptions into a reloaded/disabled plugin's stale instances.
 * <p>
 * Usage:
 * <pre>{@code
 * public final class MyPlugin extends JavaPlugin {
 *     private final BusSubscriptions bus = new BusSubscriptions();
 *
 *     @Override
 *     public void onEnable() {
 *         bus.on("economy:balance-changed", BalanceChangedPayload.class, payload -> {
 *             // handle it
 *         });
 *     }
 *
 *     @Override
 *     public void onDisable() {
 *         bus.unsubscribeAll();
 *     }
 * }
 * }</pre>
 */
public final class BusSubscriptions {

    private record Handle(String channel, UUID id) {
    }

    private final List<Handle> handles = new ArrayList<>();

    public <T> void on(@NonNull String channel, @NonNull Class<T> type, @NonNull Consumer<T> handler) {
        UUID id = RefineryBus.get().subscribe(channel, type, handler);
        handles.add(new Handle(channel, id));
    }

    public <T> void on(@NonNull String channel, @NonNull Class<T> type, @NonNull Consumer<T> handler, int priority) {
        UUID id = RefineryBus.get().subscribe(channel, type, handler, priority);
        handles.add(new Handle(channel, id));
    }

    public void unsubscribeAll() {
        for (Handle handle : handles) {
            RefineryBus.get().unsubscribe(handle.channel(), handle.id());
        }
        handles.clear();
    }
}