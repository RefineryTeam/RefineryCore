package xyz.refineryteam.refinerycore.api.storage;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds named {@link TemporaryStorage} instances so unrelated systems (combat tags,
 * pending confirmations, per-session caches, ...) don't need to each thread their own
 * static map through the codebase. One registry is typically shared per-plugin.
 * <p>
 * Usage:
 * <pre>{@code
 * StorageRegistry storage = new StorageRegistry();
 * TemporaryStorage<UUID, Long> combatTags = storage.of("combat-tags");
 * }</pre>
 */
public final class StorageRegistry {

    private final Map<String, TemporaryStorage<?, ?>> stores = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <K, V> @NonNull TemporaryStorage<K, V> of(@NonNull String name) {
        return (TemporaryStorage<K, V>) stores.computeIfAbsent(name, ignored -> new InMemoryStorage<K, V>());
    }

    public void clearAll() {
        stores.values().forEach(TemporaryStorage::clear);
    }
}