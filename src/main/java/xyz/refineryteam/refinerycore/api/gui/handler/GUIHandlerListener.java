package xyz.refineryteam.refinerycore.api.gui.handler;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.gui.RefineryGUI;
import xyz.refineryteam.refinerycore.api.listener.RefineryListener;
import xyz.refineryteam.refinerycore.plugin.RefineryCorePlugin;

public class GUIHandlerListener extends RefineryListener<RefineryCorePlugin> {

    public GUIHandlerListener(RefineryCorePlugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onInventoryClick(@NonNull InventoryClickEvent event) {
        Inventory inventory = event.getInventory();

        if (!(inventory.getHolder() instanceof RefineryGUI gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(gui.cancelClicks());
        gui.handleClick(player, event);
    }

    @EventHandler
    public void onInventoryDrag(@NonNull InventoryDragEvent event) {
        Inventory inventory = event.getInventory();

        if (!(inventory.getHolder() instanceof RefineryGUI gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(gui.cancelClicks());
    }

    @EventHandler
    public void onInventoryClose(@NonNull InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();

        if (!(inventory.getHolder() instanceof RefineryGUI gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        gui.onClose(player);
    }
}