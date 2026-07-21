package xyz.refineryteam.refinerycore.api.database;

/**
 * The dialect of a persistent {@link RefineryDatabase}.
 * <p>
 * In-memory-only, non-persistent storage is no longer modeled here — see
 * {@link xyz.refineryteam.refinerycore.api.storage.TemporaryStorage} for that.
 */
public enum DatabaseType {
    MYSQL, SQLITE, H2
}