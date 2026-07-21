package xyz.refineryteam.refinerycore.api.database.repository;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.database.RefineryDatabase;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks the repositories registered against a single {@link RefineryDatabase}, calling
 * {@link Repository#createTable()} exactly once per repository as it's registered.
 * <p>
 * Typical usage in a plugin's {@code onEnable}:
 * <pre>{@code
 * RefineryDatabase db = RefineryDatabase.sqlite(this, "data.db");
 * RepositoryRegistry registry = new RepositoryRegistry(db);
 * PlayerRepository players = registry.register(new PlayerRepository(db));
 * }</pre>
 */
public final class RepositoryRegistry {

    private final RefineryDatabase database;
    private final Map<Class<?>, Repository<?, ?>> repositories = new LinkedHashMap<>();

    public RepositoryRegistry(@NonNull RefineryDatabase database) {
        this.database = database;
    }

    /**
     * Registers a repository, creating its backing table if needed, and returns the same
     * instance for convenient chaining/assignment.
     */
    @Contract("_ -> param1")
    public <R extends Repository<?, ?>> @NonNull R register(@NonNull R repository) {
        repository.createTable();
        repositories.put(repository.getClass(), repository);
        return repository;
    }

    @SuppressWarnings("unchecked")
    public <R extends Repository<?, ?>> @NonNull R get(@NonNull Class<R> type) {
        Repository<?, ?> repository = repositories.get(type);
        if (repository == null) {
            throw new IllegalStateException("No repository of type " + type.getSimpleName() + " has been registered.");
        }
        return (R) repository;
    }

    public @NonNull RefineryDatabase database() {
        return database;
    }

    /**
     * Closes the underlying {@link RefineryDatabase}. Call this once from the owning
     * plugin's {@code onDisable}.
     */
    public void close() {
        database.close();
    }
}