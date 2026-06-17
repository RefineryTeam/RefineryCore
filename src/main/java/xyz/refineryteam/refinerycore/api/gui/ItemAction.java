package xyz.refineryteam.refinerycore.api.gui;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Getter
public class ItemAction {

    public static final ItemActionFunction NULL_ACTION_CONSUMER = (player, stack) -> {
        //
        // The null action consumer is here if the developer doesn't want the item
        // to have an action, let's say borders in a GUI where it shouldn't have actions.
        //
    };

    public interface ItemActionFunction {
        void click(Player player, ItemStack item);
    }

    private final int slot;
    private final ItemStack item;
    private final ItemActionFunction consumer;

    public ItemAction(int slot, ItemStack item, ItemActionFunction consumer) {
        this.slot = slot;
        this.item = item;
        this.consumer = consumer;
    }
}