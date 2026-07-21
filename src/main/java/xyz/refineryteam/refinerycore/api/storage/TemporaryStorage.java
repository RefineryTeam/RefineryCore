package xyz.refineryteam.refinerycore.api.storage;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * A non-persistent, in-memory key-value store scoped to a single key type, meant for data
 * that only needs to live for the current server session (or shorter, via TTLs) — combat
 * tags, cooldown-adjacent state, pending confirmations, session caches, and similar.
 * <p>
 * Unlike {@link xyz.refineryteam.refinerycore.api.database.RefineryDatabase}, nothing here
 * ever touches disk or survives a restart. Use
 * {@link xyz.refineryteam.refinerycore.api.database.repository.Repository} instead for
 * anything that needs to.
 *
 * @param <K> the key type (typically a {@link java.util.UUID} for players)
 * @param <V> the value type stored
 */
public interface TemporaryStorage<K, V> {

    void put(@NonNull K key, @NonNull V value);

    /** Stores a value that automatically expires (and is evicted) after {@code ttl} elapses. */
    void put(@NonNull K key, @NonNull V value, @NonNull Duration ttl);

    @NonNull Optional<V> get(@NonNull K key);

    boolean contains(@NonNull K key);

    void remove(@NonNull K key);

    void clear();

    @NonNull Set<K> keys();

    int size();
}