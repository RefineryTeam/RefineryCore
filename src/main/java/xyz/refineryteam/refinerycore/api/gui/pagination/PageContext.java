package xyz.refineryteam.refinerycore.api.gui.pagination;

import lombok.Getter;

import java.util.List;

@Getter
public class PageContext<T> {

    private final List<T> allItems;
    private final int pageSize;
    private int currentPage;

    public PageContext(List<T> allItems, int pageSize) {
        this.allItems = allItems;
        this.pageSize = pageSize;
        this.currentPage = 0;
    }

    public List<T> currentItems() {
        int from = currentPage * pageSize;
        int to = Math.min(from + pageSize, allItems.size());
        if (from >= allItems.size()) return List.of();
        return allItems.subList(from, to);
    }

    public boolean hasNext() {
        return (currentPage + 1) * pageSize < allItems.size();
    }

    public boolean hasPrevious() {
        return currentPage > 0;
    }

    public int totalPages() {
        return (int) Math.ceil((double) allItems.size() / pageSize);
    }

    public void next() {
        if (hasNext()) currentPage++;
    }

    public void previous() {
        if (hasPrevious()) currentPage--;
    }

    public void goTo(int page) {
        if (page >= 0 && page < totalPages()) currentPage = page;
    }
}