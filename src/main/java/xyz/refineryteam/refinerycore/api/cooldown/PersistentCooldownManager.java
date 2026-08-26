package xyz.refineryteam.refinerycore.api.cooldown;

import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.database.RefineryDatabase;
import xyz.refineryteam.refinerycore.api.database.repository.AbstractRepository;
import xyz.refineryteam.refinerycore.api.database.repository.ColumnDefinition;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@link CooldownManager} whose entries survive restarts, backed by a
 * {@link RefineryDatabase}. Use for kit/redemption/reward cooldowns where
 * "relog or restart to skip the wait" would be exploitable.
 * <p>
 * Reads are cached in memory after first load; writes go through to the
 * database immediately (fire-and-forget async by default).
 * <p>
 * Usage:
 * <pre>{@code
 * PersistentCooldownManager kits = new PersistentCooldownManager(plugin, database);
 * kits.createTable();          // once, in onEnable
 *
 * if (!kits.tryAcquire("kits", "starter", player.getUniqueId(), Duration.ofHours(24))) {
 *     long left = kits.remainingSeconds("kits", "starter", player.getUniqueId());
 *     player.sendMessage("Wait " + left + "s");
 * }
 * }</pre>
 */
public final class PersistentCooldownManager {

    private record Entry(String namespace, String key, UUID subject, long expiry) {}

    private static final class EntryRepository extends AbstractRepository<Entry, String> {
        private EntryRepository(RefineryDatabase db) {
            super(db, "refinery_cooldowns", "id", List.of(
                    ColumnDefinition.of("namespace", "VARCHAR(128) NOT NULL"),
                    ColumnDefinition.of("cooldown_key", "VARCHAR(128) NOT NULL"),
                    ColumnDefinition.of("subject", "VARCHAR(36) NOT NULL"),
                    ColumnDefinition.of("expiry", "BIGINT NOT NULL")
            ));
        }

        @Override
        protected String idColumnDefinition() {
            return "VARCHAR(255) PRIMARY KEY";
        }

        @Override
        protected Entry mapRow(ResultSet rs) throws SQLException {
            return new Entry(rs.getString("namespace"), rs.getString("cooldown_key"),
                    UUID.fromString(rs.getString("subject")), rs.getLong("expiry"));
        }

        @Override
        protected void bindAll(PreparedStatement stmt, Entry entity) throws SQLException {
            stmt.setString(1, compositeId(entity));
            stmt.setString(2, entity.namespace());
            stmt.setString(3, entity.key());
            stmt.setString(4, entity.subject().toString());
            stmt.setLong(5, entity.expiry());
        }

        @Override
        protected void bindId(PreparedStatement stmt, int index, String id) throws SQLException {
            stmt.setString(index, id);
        }

        private static String compositeId(Entry e) {
            return e.namespace() + ":" + e.key() + ":" + e.subject();
        }
    }

    private final JavaPlugin plugin;
    private final EntryRepository repository;
    // In-memory mirror: only entries that have not expired yet.
    private final CooldownManager cache = new CooldownManager();

    /**
     * Creates a manager backed by the given database. Call
     * {@link #createTable()} and {@link #loadAll()} before first use.
     *
     * @param plugin   the owning plugin; used for async persistence tasks
     * @param database the database storing cooldown rows
     */
    public PersistentCooldownManager(@NonNull JavaPlugin plugin, @NonNull RefineryDatabase database) {
        this.plugin = plugin;
        this.repository = new EntryRepository(database);
    }

    /**
     * Creates the backing table. Call once during setup.
     */
    public void createTable() {
        repository.createTable();
    }

    /**
     * Loads all unexpired rows into the memory cache. Call after
     * {@link #createTable()} in onEnable; safe to call again later.
     */
    public void loadAll() {
        List<Entry> rows = repository.findAll();
        long now = System.currentTimeMillis();
        for (Entry entry : rows) {
            if (entry.expiry() > now) {
                cache.set(entry.namespace(), entry.key(), entry.subject(),
                        Duration.ofMillis(entry.expiry() - now));
            } else {
                repository.deleteById(EntryRepository.compositeId(entry));
            }
        }
    }

    /**
     * @param namespace the feature namespace the cooldown was created under
     * @param key       the cooldown key
     * @param subject   the subject's UUID
     * @return true if an unexpired cooldown exists for this (namespace, key, subject)
     */
    public boolean isOnCooldown(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        return cache.isOnCooldown(namespace, key, subject);
    }

    /**
     * @param namespace the feature namespace
     * @param key       the cooldown key
     * @param subject   the subject's UUID
     * @return seconds left on the cooldown, or 0 if none is active
     */
    public long remainingSeconds(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        return cache.remainingSeconds(namespace, key, subject);
    }

    /**
     * @param namespace the feature namespace
     * @param key       the cooldown key
     * @param subject   the subject's UUID
     * @return milliseconds left on the cooldown, or 0 if none is active
     */
    public long remainingMillis(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        return cache.remainingMillis(namespace, key, subject);
    }

    /**
     * Sets a cooldown and persists it asynchronously.
     *
     * @param namespace the feature namespace, e.g. {@code "kits"}
     * @param key       the cooldown key, e.g. {@code "starter"}
     * @param subject   the subject's UUID
     * @param duration  how long the cooldown lasts
     */
    public void set(@NonNull String namespace, @NonNull String key, @NonNull UUID subject, @NonNull Duration duration) {
        cache.set(namespace, key, subject, duration);
        persist(namespace, key, subject,
                System.currentTimeMillis() + duration.toMillis());
    }

    /**
     * Atomically starts the cooldown unless one is already active.
     * Persists asynchronously on success.
     *
     * @param namespace the feature namespace
     * @param key       the cooldown key
     * @param subject   the subject's UUID
     * @param duration  how long the cooldown will last if acquired
     * @return true if the cooldown was newly applied (action may proceed),
     *         false if one was already active
     */
    public boolean tryAcquire(@NonNull String namespace, @NonNull String key, @NonNull UUID subject, @NonNull Duration duration) {
        if (!cache.tryAcquire(namespace, key, subject, duration)) return false;
        persist(namespace, key, subject,
                System.currentTimeMillis() + duration.toMillis());
        return true;
    }

    /**
     * Clears a cooldown from memory and deletes its row asynchronously.
     *
     * @param namespace the feature namespace
     * @param key       the cooldown key
     * @param subject   the subject's UUID
     */
    public void clear(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        cache.clear(namespace, key, subject);
        String id = namespace + ":" + key + ":" + subject;
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> repository.deleteById(id));
    }

    /**
     * Clears every cooldown for a subject (e.g. on staff pardon).
     *
     * @param subject the subject whose cooldowns are all removed
     */
    public void clearAll(@NonNull UUID subject) {
        List<Entry> doomed = new ArrayList<>();
        for (Entry entry : repository.findAll()) {
            if (entry.subject().equals(subject)) doomed.add(entry);
        }
        cache.clearAll(subject);
        plugin.getServer().getAsyncScheduler().runNow(plugin, t ->
                doomed.forEach(e -> repository.deleteById(EntryRepository.compositeId(e))));
    }

    /**
     * Deletes expired rows from the database. Call occasionally (daily
     * timer, onDisable) — the memory cache self-cleans lazily but the
     * table otherwise grows forever.
     */
    public void purgeExpired() {
        cache.purgeExpired();
        long now = System.currentTimeMillis();
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            for (Entry entry : repository.findAll()) {
                if (entry.expiry() <= now) {
                    repository.deleteById(EntryRepository.compositeId(entry));
                }
            }
        });
    }

    private void persist(@NonNull String namespace, @NonNull String key, @NonNull UUID subject, long expiry) {
        Entry entry = new Entry(namespace, key, subject, expiry);
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> repository.save(entry));
    }
}
