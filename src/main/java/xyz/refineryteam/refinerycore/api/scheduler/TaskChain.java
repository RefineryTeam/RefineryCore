package xyz.refineryteam.refinerycore.api.scheduler;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Fluent async→sync→async task sequencing for the Folia era. Each step
 * declares where it runs; the chain handles the thread hops so callers
 * stop hand-rolling {@code runAsync → getScheduler().run} nesting.
 * <p>
 * Usage:
 * <pre>{@code
 * TaskChain.of(plugin)
 *     .async(() -> database.findPlayer(name))          // async pool
 *     .abortIfNull(player -> warnNotFound(name))       // bail with a callback
 *     .sync(player -> applyTitle(player))              // global region thread
 *     .async(player -> logToWebhook(player))           // back to async
 *     .execute();
 * }
 * }</pre>
 * The current value flows through the chain; each step transforms it.
 *
 * @param <T> the value currently flowing through the chain
 */
public final class TaskChain<T> {

    private final JavaPlugin plugin;
    private final TaskChain<?> root;
    // Runs the remaining chain with the given value, on whatever thread
    // the previous step ended on.
    private final Consumer<ChainContext> link;

    private record ChainContext(@Nullable Object value, @Nullable Runnable abortAction) {}

    private volatile boolean aborted = false;

    private TaskChain(JavaPlugin plugin, TaskChain<?> root, Consumer<ChainContext> link) {
        this.plugin = plugin;
        this.root = root != null ? root : this;
        this.link = link;
    }

    /**
     * Starts a new chain rooted on the given plugin.
     */
    public static @NonNull TaskChain<Void> of(@NonNull JavaPlugin plugin) {
        return new TaskChain<>(plugin, null, ctx -> {});
    }

    // ---- step builders -------------------------------------------------

    /**
     * Runs a supplier on the async scheduler and passes its result onward.
     */
    public <R> @NonNull TaskChain<R> async(@NonNull Supplier<R> work) {
        return new TaskChain<>(plugin, root, ctx -> {
            if (root.aborted) return;
            plugin.getServer().getAsyncScheduler().runNow(plugin, t ->
                    advance(work.get()));
        });
    }

    /**
     * Runs a transformation on the global region (main) thread.
     */
    public <R> @NonNull TaskChain<R> sync(@NonNull Function<T, R> work) {
        return new TaskChain<>(plugin, root, ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t ->
                    advance(work.apply(value)));
        });
    }

    /**
     * Runs a side-effecting step on the main thread without changing the
     * flowing value.
     */
    public @NonNull TaskChain<T> syncConsume(@NonNull Consumer<T> work) {
        return sync(v -> { work.accept(v); return v; });
    }

    /**
     * Runs a side-effecting step on the async pool without changing the
     * flowing value.
     */
    public @NonNull TaskChain<T> asyncConsume(@NonNull Consumer<T> work) {
        return new TaskChain<>(plugin, root, ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                work.accept(value);
                advance(value);
            });
        });
    }

    /**
     * Aborts the chain if the current value is null, invoking the given
     * handler first (on the current thread).
     */
    public @NonNull TaskChain<T> abortIfNull(@Nullable Consumer<T> onAbort) {
        return new TaskChain<>(plugin, root, ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            if (value == null) {
                root.aborted = true;
                if (onAbort != null) onAbort.accept(null);
                return;
            }
            link.accept(ctx);
        });
    }

    /**
     * Aborts the chain if the predicate fails, invoking the given handler
     * first (on the current thread).
     */
    public @NonNull TaskChain<T> abortIf(@NonNull Predicate<T> condition, @Nullable Consumer<T> onAbort) {
        return new TaskChain<>(plugin, root, ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            if (!condition.test(value)) {
                root.aborted = true;
                if (onAbort != null) onAbort.accept(value);
                return;
            }
            link.accept(ctx);
        });
    }

    /**
     * Registers an error handler for any exception thrown inside any step.
     * Without one, exceptions are logged to the plugin logger.
     */
    public @NonNull TaskChain<T> onError(@NonNull Consumer<Throwable> handler) {
        errorHandler = handler;
        return this;
    }

    private volatile Consumer<Throwable> errorHandler;

    /**
     * Kicks off execution. Chains are single-use — calling execute twice
     * throws.
     */
    public void execute() {
        if (executed) throw new IllegalStateException("TaskChain already executed");
        executed = true;
        link.accept(new ChainContext(null, null));
    }

    private volatile boolean executed = false;

    private void advance(@Nullable Object value) {
        try {
            link.accept(new ChainContext(value, null));
        } catch (Throwable t) {
            Consumer<Throwable> handler = root.errorHandler;
            if (handler != null) handler.accept(t);
            else plugin.getLogger().severe("TaskChain step failed: " + t);
        }
    }

    // Entity-scoped variant helpers --------------------------------------

    /**
     * Runs a step on the region thread that owns the given entity
     * (Folia-safe entity access). Falls back to the global scheduler when
     * the entity retires before the step runs.
     */
    public <R> @NonNull TaskChain<R> atEntity(@NonNull Entity entity, @NonNull Function<T, R> work) {
        return new TaskChain<>(plugin, root, ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            var scheduled = entity.getScheduler().run(plugin, t ->
                    advance(work.apply(value)), null);
            if (scheduled == null) {
                // Entity retired — skip the step but keep the chain alive.
                advance(value);
            }
        });
    }
}
