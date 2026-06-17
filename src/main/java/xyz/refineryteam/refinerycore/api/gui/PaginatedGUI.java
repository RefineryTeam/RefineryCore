package xyz.refineryteam.refinerycore.api.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import xyz.refineryteam.refinerycore.api.gui.pagination.PageContext;

import java.util.List;

public abstract class PaginatedGUI<T> extends RefineryGUI {

    private PageContext<T> pageContext;

    protected PaginatedGUI(int slots, String title, List<T> items, int pageSize) {
        super(slots, title);
        this.pageContext = new PageContext<>(items, pageSize);
    }

    protected PaginatedGUI(InventoryType type, String title, List<T> items, int pageSize) {
        super(type, title);
        this.pageContext = new PageContext<>(items, pageSize);
    }

    @Override
    public void onInitialize(Player player) {
        onPageInitialize(player, pageContext);
        renderPage(player);
    }

    private void renderPage(Player player) {
        List<T> items = pageContext.currentItems();
        int[] contentSlots = contentSlots();
        for (int i = 0; i < contentSlots.length; i++) {
            if (i < items.size()) {
                setItem(contentSlots[i], renderItem(player, items.get(i), i));
                onSlot(contentSlots[i], (p, e) -> {
                    int idx = getSlotIndex(e.getRawSlot());
                    if (idx >= 0 && idx < pageContext.currentItems().size()) {
                        onItemClick(p, pageContext.currentItems().get(idx), idx, e);
                    }
                });
            } else {
                setItem(contentSlots[i], emptySlotItem(contentSlots[i]));
            }
        }

        setItem(previousSlot(), pageContext.hasPrevious() ? previousPageItem() : null, (p, e) -> {
            if (pageContext.hasPrevious()) {
                pageContext.previous();
                refresh(p);
            }
        });

        setItem(nextSlot(), pageContext.hasNext() ? nextPageItem() : null, (p, e) -> {
            if (pageContext.hasNext()) {
                pageContext.next();
                refresh(p);
            }
        });
    }

    private int getSlotIndex(int rawSlot) {
        int[] slots = contentSlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == rawSlot) return i;
        }
        return -1;
    }

    public void updateItems(List<T> newItems) {
        this.pageContext = new PageContext<>(newItems, pageContext.getPageSize());
    }

    public PageContext<T> getPageContext() {
        return pageContext;
    }

    protected abstract int[] contentSlots();
    protected abstract ItemStack renderItem(Player player, T item, int index);
    protected abstract void onItemClick(Player player, T item, int index, org.bukkit.event.inventory.InventoryClickEvent event);
    protected abstract ItemStack previousPageItem();
    protected abstract ItemStack nextPageItem();

    protected void onPageInitialize(Player player, PageContext<T> context) {}
    protected ItemStack emptySlotItem(int slot) { return null; }
    protected int previousSlot() { return getInventory().getSize() - 9; }
    protected int nextSlot() { return getInventory().getSize() - 1; }
}