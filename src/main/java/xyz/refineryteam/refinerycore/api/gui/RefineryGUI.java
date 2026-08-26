package xyz.refineryteam.refinerycore.api.gui;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.refineryteam.refinerycore.api.gui.annotation.SlotAction;
import xyz.refineryteam.refinerycore.api.gui.annotation.SlotItem;
import xyz.refineryteam.refinerycore.api.gui.layout.GUILayout;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class RefineryGUI implements InventoryHolder {

    public static void open(@NonNull Player player, @NonNull RefineryGUI gui) {
        gui.initialize(player);
        player.openInventory(gui.inventory);
    }

    @Getter
    private Inventory inventory;

    private volatile boolean destroyed = false;

    private final Map<Integer, ItemStack> itemMap = new HashMap<>();
    private final Map<Integer, GUIClickHandler> clickMap = new HashMap<>();

    protected RefineryGUI(int slots, String title) {
        this.inventory = Bukkit.createInventory(this, slots, EasyMiniMessage.format(title));
    }

    protected RefineryGUI(InventoryType type, String title) {
        this.inventory = Bukkit.createInventory(this, type, EasyMiniMessage.format(title));
    }

    private void initialize(Player player) {
        itemMap.clear();
        clickMap.clear();
        inventory.clear();

        onInitialize(player);
        scanAnnotations();
        applyLayouts();
        flushToInventory();
        onOpen(player);
    }

    private void scanAnnotations() {
        for (Method method : getClass().getDeclaredMethods()) {
            method.setAccessible(true);

            if (method.isAnnotationPresent(SlotItem.class)) {
                SlotItem annotation = method.getAnnotation(SlotItem.class);
                for (int slot : annotation.slots()) {
                    try {
                        Object result = method.invoke(this);
                        if (result instanceof ItemStack stack) {
                            itemMap.put(slot, stack);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to invoke @SlotItem method " + method.getName(), e);
                    }
                }
            }

            if (method.isAnnotationPresent(SlotAction.class)) {
                SlotAction annotation = method.getAnnotation(SlotAction.class);
                for (int slot : annotation.slots()) {
                    clickMap.put(slot, buildClickHandler(method));
                }
            }
        }
    }

    @Contract(pure = true)
    private @NonNull GUIClickHandler buildClickHandler(@NonNull Method method) {
        Class<?>[] params = method.getParameterTypes();
        return (player, event) -> {
            try {
                if (params.length == 0) {
                    method.invoke(this);
                } else if (params.length == 1 && params[0] == Player.class) {
                    method.invoke(this, player);
                } else if (params.length == 1 && params[0] == InventoryClickEvent.class) {
                    method.invoke(this, event);
                } else if (params.length == 2 && params[0] == Player.class && params[1] == InventoryClickEvent.class) {
                    method.invoke(this, player, event);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke @SlotAction method " + method.getName(), e);
            }
        };
    }

    private void applyLayouts() {
        for (GUILayout layout : layouts()) {
            layout.applyTo(this);
        }
    }

    private void flushToInventory() {
        itemMap.forEach(inventory::setItem);
    }

    protected GUILayout[] layouts() {
        return new GUILayout[0];
    }

    public abstract void onInitialize(Player player);
    public void onOpen(Player player) {}
    public void onDestroy() {}
    public void onClose(Player player) {}
    public boolean cancelClicks() { return true; }

    public void handleClick(Player player, @NonNull InventoryClickEvent event) {
        if (destroyed) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        GUIClickHandler handler = clickMap.get(slot);
        if (handler != null) handler.handle(player, event);
    }

    public RefineryGUI setItem(int slot, @Nullable ItemStack item, @Nullable GUIClickHandler handler) {
        if (destroyed) return this;
        if (item != null) itemMap.put(slot, item);
        else itemMap.remove(slot);
        if (handler != null) clickMap.put(slot, handler);
        if (inventory != null) inventory.setItem(slot, item);
        return this;
    }

    public RefineryGUI setItem(int slot, @Nullable ItemStack item) {
        return setItem(slot, item, null);
    }

    public RefineryGUI onSlot(int slot, @NonNull GUIClickHandler handler) {
        clickMap.put(slot, handler);
        return this;
    }

    public void refresh(Player player) {
        if (destroyed) return;
        initialize(player);
    }

    public Map<Integer, ItemStack> getItemMap() {
        return Collections.unmodifiableMap(itemMap);
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        onDestroy();
        new ArrayList<>(inventory.getViewers()).forEach(HumanEntity::closeInventory);
        itemMap.clear();
        clickMap.clear();
        // Keep the inventory reference: Bukkit may still deliver events for it,
        // and InventoryHolder#getInventory() must not return null. The
        // destroyed flag guards all post-destroy access instead.
    }

    /**
     * @return true once {@link #destroy()} has been called. Events and
     * refreshes targeting a destroyed GUI are ignored.
     */
    public boolean isDestroyed() {
        return destroyed;
    }

    @FunctionalInterface
    public interface GUIClickHandler {
        void handle(Player player, InventoryClickEvent event);
    }
}