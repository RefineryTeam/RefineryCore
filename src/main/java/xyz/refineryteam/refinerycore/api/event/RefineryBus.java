package xyz.refineryteam.refinerycore.api.event;

import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A process-wide, string-keyed pub/sub bus for cross-plugin communication
 * that doesn't require a hard compile-time dependency between plugins.
 * <p>
 * Unlike Bukkit events, channels here are just strings, so plugin A can
 * publish on {@code "economy:balance-changed"} and plugin B can subscribe
 * to it without either plugin knowing the other's classes exist. This is
 * meant for lightweight signaling (notify, refresh, sync) — for anything
 * that needs to be cancellable or intercepted mid-flow, use a real Bukkit
 * event instead.
 * <p>
 * Usage (publisher, e.g. an economy plugin):
 * <pre>{@code
 * RefineryBus.get().publish("economy:balance-changed", new BalanceChangedPayload(uuid, newBalance));
 * }</pre>
 * Usage (subscriber, e.g. a cosmetics plugin reacting to balance changes):
 * <pre>{@code
 * RefineryBus.get().subscribe("economy:balance-changed", BalanceChangedPayload.class, payload -> {
 *     // react without depending on the economy plugin's jar
 * });
 * }</pre>
 * Payloads are plain objects (records work well) shared by convention —
 * both sides need to agree on the shape, typically via a small shared
 * "API" module or by documenting the payload class per channel.
 */
public final class RefineryBus {

    private static final RefineryBus INSTANCE = new RefineryBus();

    private final Map<String, List<Subscription<?>>> subscribers = new ConcurrentHashMap<>();

    private RefineryBus() {
    }

    public static @NonNull RefineryBus get() {
        return INSTANCE;
    }

    /**
     * Publishes an event on the given channel. Delivery is synchronous,
     * on the calling thread, in subscriber priority order (highest first).
     * A throwing subscriber does not prevent others from receiving it.
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(@NonNull String channel, @NonNull T payload) {
        List<Subscription<?>> handlers = subscribers.get(channel);
        if (handlers == null || handlers.isEmpty()) return;

        for (Subscription<?> subscription : handlers) {
            if (!subscription.type().isInstance(payload)) continue;
            try {
                ((Subscription<T>) subscription).handler().accept(payload);
            } catch (Exception e) {
                System.getLogger(RefineryBus.class.getName())
                        .log(System.Logger.Level.ERROR, "Subscriber on channel '" + channel + "' threw an exception", e);
            }
        }
    }

    /**
     * Subscribes to a channel. Only payloads assignable to {@code type}
     * are delivered to {@code handler}, so multiple unrelated payload
     * shapes can share a channel name if needed (though separate channels
     * are usually clearer).
     *
     * @return a handle that can be passed to {@link #unsubscribe(String, UUID)}
     */
    public <T> @NonNull UUID subscribe(@NonNull String channel, @NonNull Class<T> type, @NonNull Consumer<T> handler) {
        return subscribe(channel, type, handler, 0);
    }

    /**
     * Same as {@link #subscribe(String, Class, Consumer)} but with an
     * explicit priority; higher values are notified first.
     */
    public <T> @NonNull UUID subscribe(@NonNull String channel, @NonNull Class<T> type, @NonNull Consumer<T> handler, int priority) {
        UUID id = UUID.randomUUID();
        Subscription<T> subscription = new Subscription<>(id, type, handler, priority);

        subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(subscription);
        subscribers.get(channel).sort(Comparator.<Subscription<?>>comparingInt(Subscription::priority).reversed());

        return id;
    }

    public void unsubscribe(@NonNull String channel, @NonNull UUID subscriptionId) {
        List<Subscription<?>> handlers = subscribers.get(channel);
        if (handlers == null) return;
        handlers.removeIf(s -> s.id().equals(subscriptionId));
    }

    /**
     * Removes every subscription on a channel. Mainly useful for tests or
     * a full reset; prefer {@link #unsubscribe(String, UUID)} in plugin
     * code so you don't tear down other plugins' subscriptions.
     */
    public void clearChannel(@NonNull String channel) {
        subscribers.remove(channel);
    }

    public boolean hasSubscribers(@NonNull String channel) {
        List<Subscription<?>> handlers = subscribers.get(channel);
        return handlers != null && !handlers.isEmpty();
    }

    private record Subscription<T>(UUID id, Class<T> type, Consumer<T> handler, int priority) {
    }
}