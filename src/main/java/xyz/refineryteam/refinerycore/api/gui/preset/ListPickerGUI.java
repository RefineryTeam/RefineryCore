package xyz.refineryteam.refinerycore.api.gui.preset;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.refineryteam.refinerycore.api.gui.PaginatedGUI;
import xyz.refineryteam.refinerycore.api.item.ItemBuilder;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A ready-made paginated picker: renders one {@link ItemStack} per element
 * of a list and invokes a callback when the player clicks one. Covers the
 * extremely common "pick a player / kit / warp / home from a menu" flow
 * without writing a GUI subclass.
 * <p>
 * Usage:
 * <pre>{@code
 * ListPickerGUI.of(
 *         "<dark_gray>Select a warp",
 *         warps,
 *         warp -> ItemBuilder.of(Material.ENDER_PEARL)
 *             .name("<gold>" + warp.name())
 *             .build(),
 *         (player, warp) -> teleport(player, warp))
 *     .open(player);
 * }</pre>
 *
 * @param <T> the element type being picked from
 */
public final class ListPickerGUI<T> extends PaginatedGUI<T> {

    private static final int[] DEFAULT_CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final Function<T, ItemStack> renderer;
    private final BiConsumer<Player, T> onSelect;
    private final int[] contentSlots;

    private ListPickerGUI(@NonNull String title, @NonNull List<T> items, int pageSize,
                          @NonNull Function<T, ItemStack> renderer,
                          @NonNull BiConsumer<Player, T> onSelect,
                          int @NonNull [] contentSlots) {
        super(45, title, items, pageSize);
        this.renderer = renderer;
        this.onSelect = onSelect;
        this.contentSlots = contentSlots.clone();
    }

    /**
     * Creates a picker with the default 3-row content area (21 items per page).
     */
    public static <T> @NonNull ListPickerGUI<T> of(@NonNull String title, @NonNull List<T> items,
                                                   @NonNull Function<T, ItemStack> renderer,
                                                   @NonNull BiConsumer<Player, T> onSelect) {
        return new ListPickerGUI<>(title, items, DEFAULT_CONTENT_SLOTS.length, renderer, onSelect, DEFAULT_CONTENT_SLOTS);
    }

    /**
     * Creates a picker with a custom content slot layout. The page size is
     * derived from the number of slots.
     */
    public static <T> @NonNull ListPickerGUI<T> of(@NonNull String title, @NonNull List<T> items,
                                                   @NonNull Function<T, ItemStack> renderer,
                                                   @NonNull BiConsumer<Player, T> onSelect,
                                                   int @NonNull [] contentSlots) {
        return new ListPickerGUI<>(title, items, contentSlots.length, renderer, onSelect, contentSlots);
    }

    @Override
    protected int[] contentSlots() {
        return contentSlots;
    }

    @Override
    protected @NonNull ItemStack renderItem(Player player, T item, int index) {
        return renderer.apply(item);
    }

    @Override
    protected void onItemClick(Player player, T item, int index, InventoryClickEvent event) {
        player.closeInventory();
        onSelect.accept(player, item);
    }

    @Override
    protected @NonNull ItemStack previousPageItem() {
        return navItem("<yellow>◀ Previous page");
    }

    @Override
    protected @NonNull ItemStack nextPageItem() {
        return navItem("<yellow>Next page ▶");
    }

    private @NonNull ItemStack navItem(@NonNull String name) {
        return ItemBuilder.of(Material.ARROW).name(name).build();
    }
}
