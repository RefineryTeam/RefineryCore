package xyz.refineryteam.refinerycore.api.gui.pagination;

import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.gui.PaginatedGUI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps a {@link PaginatedGUI}'s item list in sync with a live backing
 * collection. Instead of copying the list into the GUI once at construction,
 * a binder re-reads it from a supplier on every page render — so GUIs
 * showing "all online players", "all warps", etc. stay current without
 * manual {@code updateItems} calls.
 * <p>
 * Usage:
 * <pre>{@code
 * public class OnlinePlayersGUI extends PaginatedGUI<Player> {
 *     public OnlinePlayersGUI() {
 *         super(45, "<dark_gray>Online players",
 *               List.of(), Bukkit.getMaxPlayers());
 *         CollectionBinder.bind(this, () -> new ArrayList<>(Bukkit.getOnlinePlayers()));
 *     }
 * }
 * }</pre>
 * Or standalone with change notifications:
 * <pre>{@code
 * CollectionBinder<Player> binder = CollectionBinder.of(gui, () -> fetchItems());
 * binder.onChange(gui::refreshFor); // re-render whenever you call binder.poll()
 * }</pre>
 */
public final class CollectionBinder<T> {

    private final Supplier<List<T>> source;
    private final List<Consumer<List<T>>> listeners = new ArrayList<>();
    private List<T> lastSnapshot = List.of();

    private CollectionBinder(@NonNull Supplier<List<T>> source) {
        this.source = source;
    }

    /**
     * Creates a binder around a live supplier and immediately pushes its
     * first snapshot into the GUI's page context.
     *
     * @param gui    the paginated GUI to feed
     * @param source supplies a fresh copy of the items on each read;
     *               return a new list, never a live view being mutated
     * @param <T>    the element type
     * @return a bound binder; call {@link #snapshot()} whenever the backing
     *         data may have changed (e.g. at the top of {@code onInitialize})
     */
    public static <T> @NonNull CollectionBinder<T> bind(@NonNull PaginatedGUI<T> gui, @NonNull Supplier<List<T>> source) {
        CollectionBinder<T> binder = new CollectionBinder<>(source);
        gui.updateItems(binder.snapshot());
        return binder;
    }

    /**
     * Creates an unbound binder; call {@link #snapshot()} yourself wherever
     * it makes sense (e.g. inside {@code onInitialize}).
     *
     * @param source supplies a fresh copy of the items on each read
     * @param <T>    the element type
     * @return a new unbound binder
     */
    public static <T> @NonNull CollectionBinder<T> of(@NonNull Supplier<List<T>> source) {
        return new CollectionBinder<>(source);
    }

    /**
     * Re-reads the backing collection, notifies change listeners if the
     * snapshot differs from the previous one, and returns it.
     *
     * @return an immutable snapshot of the current backing collection
     */
    public @NonNull List<T> snapshot() {
        List<T> fresh = List.copyOf(source.get());

        if (!fresh.equals(lastSnapshot)) {
            lastSnapshot = fresh;
            for (Consumer<List<T>> listener : listeners) {
                listener.accept(fresh);
            }
        }
        return fresh;
    }

    /**
     * Registers a listener fired by {@link #snapshot()} when the backing
     * collection has changed since the last read.
     *
     * @param listener receives the new snapshot on change
     * @return this binder, for chaining
     */
    public @NonNull CollectionBinder<T> onChange(@NonNull Consumer<List<T>> listener) {
        listeners.add(listener);
        return this;
    }

    /**
     * @return the most recent snapshot without re-reading the source.
     */
    public @NonNull List<T> lastSnapshot() {
        return lastSnapshot;
    }
}
