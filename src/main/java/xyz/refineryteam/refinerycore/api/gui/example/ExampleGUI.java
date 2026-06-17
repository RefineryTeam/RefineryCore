package xyz.refineryteam.refinerycore.api.gui.example;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.gui.RefineryGUI;
import xyz.refineryteam.refinerycore.api.gui.annotation.SlotAction;
import xyz.refineryteam.refinerycore.api.gui.annotation.SlotItem;
import xyz.refineryteam.refinerycore.api.gui.layout.GUILayout;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;
import xyz.refineryteam.refinerycore.api.item.ItemBuilder;

public class ExampleGUI extends RefineryGUI {

    private final Player target;

    public ExampleGUI(Player target) {
        super(27, "<bold>Example</bold>");
        this.target = target;
    }

    @Override
    public GUILayout[] layouts() {
        return new GUILayout[]{ GUILayout.border(3, new ItemStack(Material.BLACK_STAINED_GLASS_PANE)) };
    }

    @SlotItem(slots = { 13 })
    public ItemStack profileItem() {
        return ItemBuilder.of(Material.PLAYER_HEAD)
            .name("<yellow>" + target.getName())
            .build();
    }

    @SlotAction(slots = { 13 })
    public void onProfileClick(@NonNull Player clicker) {
        clicker.sendMessage(EasyMiniMessage.format("<green>Clicked profile!"));
    }

    @Override
    public void onInitialize(Player player) {}
}