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
    private final Consumer<ChainContext> step;
    private volatile TaskChain<?> next;

    private record ChainContext(@Nullable Object value, @Nullable Runnable abortAction) {}

    private volatile boolean aborted = false;

    private TaskChain(JavaPlugin plugin, TaskChain<?> root, Consumer<ChainContext> step) {
        this.plugin = plugin;
        this.root = root != null ? root : this;
        this.step = step;
    }

    /**
     * Starts a new chain rooted on the given plugin.
     *
     * @param plugin the plugin owning all scheduled steps
     * @return a new empty chain; the flowing value starts as null
     */
    public static @NonNull TaskChain<Void> of(@NonNull JavaPlugin plugin) {
        return new TaskChain<>(plugin, null, ctx -> {});
    }

    /**
     * Runs a supplier on the async scheduler and passes its result onward.
     *
     * @param work the async computation
     * @param <R>  the result type flowing to the next step
     * @return a new chain link; call further steps or {@link #execute()}
     */
    public <R> @NonNull TaskChain<R> async(@NonNull Supplier<R> work) {
        return append(ctx -> {
            if (root.aborted) return;
            plugin.getServer().getAsyncScheduler().runNow(plugin, t ->
                    runSafely(() -> advance(work.get())));
        });
    }

    /**
     * Runs a transformation on the global region (main) thread.
     *
     * @param work the transformation applied on the main thread
     * @param <R>  the result type flowing to the next step
     * @return a new chain link
     */
    public <R> @NonNull TaskChain<R> sync(@NonNull Function<T, R> work) {
        return append(ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t ->
                    runSafely(() -> advance(work.apply(value))));
        });
    }

    /**
     * Runs a side-effecting step on the main thread without changing the
     * flowing value.
     *
     * @param work the action applied on the main thread
     * @return a new chain link with the same flowing value
     */
    public @NonNull TaskChain<T> syncConsume(@NonNull Consumer<T> work) {
        return sync(v -> { work.accept(v); return v; });
    }

    /**
     * Runs a side-effecting step on the async pool without changing the
     * flowing value.
     *
     * @param work the action applied on an async thread
     * @return a new chain link with the same flowing value
     */
    public @NonNull TaskChain<T> asyncConsume(@NonNull Consumer<T> work) {
        return append(ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                runSafely(() -> {
                    work.accept(value);
                    advance(value);
                });
            });
        });
    }

    /**
     * Aborts the chain if the current value is null, invoking the given
     * handler first (on the current thread).
     *
     * @param onAbort invoked when aborting; receives null; may be null itself
     * @return a new chain link that only continues when the value is non-null
     */
    public @NonNull TaskChain<T> abortIfNull(@Nullable Consumer<T> onAbort) {
        return append(ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            if (value == null) {
                root.aborted = true;
                if (onAbort != null) onAbort.accept(null);
                return;
            }
            advance(value);
        });
    }

    /**
     * Aborts the chain if the predicate fails, invoking the given handler
     * first (on the current thread).
     *
     * @param condition predicate the current value must pass to continue
     * @param onAbort   invoked when aborting; may be null
     * @return a new chain link that only continues when the condition holds
     */
    public @NonNull TaskChain<T> abortIf(@NonNull Predicate<T> condition, @Nullable Consumer<T> onAbort) {
        return append(ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            if (!condition.test(value)) {
                root.aborted = true;
                if (onAbort != null) onAbort.accept(value);
                return;
            }
            advance(value);
        });
    }

    /**
     * Registers an error handler for any exception thrown inside any step.
     * Without one, exceptions are logged to the plugin logger.
     *
     * @param handler receives the thrown throwable
     * @return this chain link
     */
    public @NonNull TaskChain<T> onError(@NonNull Consumer<Throwable> handler) {
        root.errorHandler = handler;
        return this;
    }

    private volatile Consumer<Throwable> errorHandler;

    /**
     * Kicks off execution. Chains are single-use — calling execute twice
     * throws.
     */
    public void execute() {
        if (root.executed) throw new IllegalStateException("TaskChain already executed");
        root.executed = true;
        root.advance(null);
    }

    private volatile boolean executed = false;

    private void advance(@Nullable Object value) {
        TaskChain<?> following = next;
        if (following == null) return;
        following.runStep(new ChainContext(value, null));
    }

    private void runStep(@NonNull ChainContext context) {
        try {
            step.accept(context);
        } catch (Throwable t) {
            handleError(t);
        }
    }

    private void runSafely(@NonNull Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            handleError(t);
        }
    }

    private void handleError(@NonNull Throwable throwable) {
        Consumer<Throwable> handler = root.errorHandler;
        if (handler != null) handler.accept(throwable);
        else plugin.getLogger().severe("TaskChain step failed: " + throwable);
    }

    private <R> @NonNull TaskChain<R> append(@NonNull Consumer<ChainContext> step) {
        TaskChain<R> following = new TaskChain<>(plugin, root, step);
        next = following;
        return following;
    }

    // Entity-scoped variant helpers --------------------------------------

    /**
     * Runs a step on the region thread that owns the given entity
     * (Folia-safe entity access). Falls back to skipping the step when the
     * entity retires before it runs.
     *
     * @param entity the entity whose region thread runs the work
     * @param work   the transformation applied on the entity's thread
     * @param <R>    the result type flowing to the next step
     * @return a new chain link
     */
    public <R> @NonNull TaskChain<R> atEntity(@NonNull Entity entity, @NonNull Function<T, R> work) {
        return append(ctx -> {
            if (root.aborted) return;
            @SuppressWarnings("unchecked")
            T value = (T) ctx.value();
            var scheduled = entity.getScheduler().run(plugin, t ->
                    runSafely(() -> advance(work.apply(value))), null);
            if (scheduled == null) {
                // Entity retired — skip the step but keep the chain alive.
                advance(value);
            }
        });
    }
}
