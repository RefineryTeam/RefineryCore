package xyz.refineryteam.refinerycore.api.database.repository;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A repository abstraction over a single database-backed entity type, in the style of
 * Spring Data / DAO repositories. Implementations translate calls into dialect-correct SQL
 * for whichever {@link xyz.refineryteam.refinerycore.api.database.DatabaseType} the backing
 * {@link xyz.refineryteam.refinerycore.api.database.RefineryDatabase} was opened as, so
 * calling code never needs to branch on database type.
 * <p>
 * All methods have blocking and {@code Async} variants; the async variants run on the
 * common ForkJoinPool via {@link CompletableFuture} and should be preferred from anywhere
 * that isn't already off the main server thread.
 *
 * @param <T>  the entity type
 * @param <ID> the entity's primary key type
 */
public interface Repository<T, ID> {

    /**
     * Ensures the backing table (and any indices) exist. Called once by
     * {@link RepositoryRegistry} when the repository is registered; safe to call again.
     */
    void createTable();

    Optional<T> findById(@NonNull ID id);

    @NonNull List<T> findAll();

    /**
     * Inserts a new row, or updates the existing row with the same primary key if one
     * already exists (an "upsert"), using each dialect's native syntax.
     */
    T save(@NonNull T entity);

    void deleteById(@NonNull ID id);

    boolean existsById(@NonNull ID id);

    long count();

    default @NonNull CompletableFuture<Optional<T>> findByIdAsync(@NonNull ID id) {
        return CompletableFuture.supplyAsync(() -> findById(id));
    }

    default @NonNull CompletableFuture<List<T>> findAllAsync() {
        return CompletableFuture.supplyAsync(this::findAll);
    }

    default @NonNull CompletableFuture<T> saveAsync(@NonNull T entity) {
        return CompletableFuture.supplyAsync(() -> save(entity));
    }

    default @NonNull CompletableFuture<Void> deleteByIdAsync(@NonNull ID id) {
        return CompletableFuture.runAsync(() -> deleteById(id));
    }

    default @NonNull CompletableFuture<Boolean> existsByIdAsync(@NonNull ID id) {
        return CompletableFuture.supplyAsync(() -> existsById(id));
    }

    default @NonNull CompletableFuture<Long> countAsync() {
        return CompletableFuture.supplyAsync(this::count);
    }
}