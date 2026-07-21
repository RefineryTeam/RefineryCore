package xyz.refineryteam.refinerycore.api.storage;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link TemporaryStorage} implementation: a {@link ConcurrentHashMap} with
 * optional per-entry expiry. Expired entries are cleaned up lazily on access as well as
 * proactively — see {@link #startCleanupTask}.
 */
public final class InMemoryStorage<K, V> implements TemporaryStorage<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();

    @Override
    public void put(@NonNull K key, @NonNull V value) {
        map.put(key, new Entry<>(value, null));
    }

    @Override
    public void put(@NonNull K key, @NonNull V value, @NonNull Duration ttl) {
        map.put(key, new Entry<>(value, Instant.now().plus(ttl)));
    }

    @Override
    public @NonNull Optional<V> get(@NonNull K key) {
        Entry<V> entry = map.get(key);
        if (entry == null) return Optional.empty();
        if (entry.isExpired()) {
            map.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public boolean contains(@NonNull K key) {
        return get(key).isPresent();
    }

    @Override
    public void remove(@NonNull K key) {
        map.remove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public @NonNull Set<K> keys() {
        purgeExpired();
        return new HashSet<>(map.keySet());
    }

    @Override
    public int size() {
        purgeExpired();
        return map.size();
    }

    private void purgeExpired() {
        map.forEach((key, entry) -> {
            if (entry.isExpired()) map.remove(key, entry);
        });
    }

    /**
     * Schedules a repeating Bukkit task on the given plugin that purges expired entries
     * every {@code period}, so entries with a TTL don't just linger until their key is next
     * looked up. Purely a memory-hygiene convenience — {@link #get} already treats expired
     * entries as absent regardless of whether this is called.
     */
    public void startCleanupTask(org.bukkit.plugin.java.@NonNull JavaPlugin plugin, long periodTicks) {
        org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::purgeExpired, periodTicks, periodTicks);
    }
}