package xyz.refineryteam.refinerycore.api.gui.layout;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.gui.RefineryGUI;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class GUILayout {

    private final Map<Integer, ItemStack> staticItems = new HashMap<>();
    private final Map<Integer, Consumer<RefineryGUI>> appliers = new HashMap<>();

    public static @NonNull GUILayout border(int rows, ItemStack filler) {
        GUILayout layout = new GUILayout();
        int size = rows * 9;
        for (int i = 0; i < 9; i++) layout.staticItems.put(i, filler);
        for (int i = size - 9; i < size; i++) layout.staticItems.put(i, filler);
        for (int i = 1; i < rows - 1; i++) {
            layout.staticItems.put(i * 9, filler);
            layout.staticItems.put(i * 9 + 8, filler);
        }
        return layout;
    }

    public static @NonNull GUILayout fill(int size, ItemStack filler) {
        GUILayout layout = new GUILayout();
        for (int i = 0; i < size; i++) layout.staticItems.put(i, filler);
        return layout;
    }

    public GUILayout slot(int slot, ItemStack item) {
        staticItems.put(slot, item);
        return this;
    }

    public GUILayout slot(int slot, Consumer<RefineryGUI> applier) {
        appliers.put(slot, applier);
        return this;
    }

    public GUILayout merge(@NonNull GUILayout other) {
        staticItems.putAll(other.staticItems);
        appliers.putAll(other.appliers);
        return this;
    }

    public void applyTo(RefineryGUI gui) {
        staticItems.forEach((slot, item) -> gui.setItem(slot, item, null));
        appliers.forEach((slot, applier) -> applier.accept(gui));
    }
}