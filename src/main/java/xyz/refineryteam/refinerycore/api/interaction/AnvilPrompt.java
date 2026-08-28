package xyz.refineryteam.refinerycore.api.interaction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.item.ItemBuilder;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Single-line text input via a real anvil GUI instead of chat — useful
 * when you don't want to interrupt/leave the inventory UI (renaming an
 * item, entering a search filter or numeric amount, naming a kit, etc)
 * and don't want the answer to flash through public chat.
 * <p>
 * Usage:
 * <pre>{@code
 * AnvilPrompt.builder(plugin)
 *     .title("<gray>Enter amount")
 *     .initialText("64")
 *     .validate(text -> text.matches("\\d+"), "<red>Numbers only.")
 *     .onInput((player, text) -> giveAmount(player, Integer.parseInt(text)))
 *     .onCancel(player -> player.sendMessage("Cancelled."))
 *     .open(player);
 * }</pre>
 */
public final class AnvilPrompt implements Listener {

    private final Plugin plugin;
    private final String title;
    private final String initialText;
    private final Predicate<String> validator;
    private final String validationFailMessage;
    private final java.util.function.BiConsumer<Player, String> onInput;
    private final Consumer<Player> onCancel;

    private boolean resolved = false;
    private AnvilInventory inventory;

    private AnvilPrompt(Builder builder) {
        this.plugin = builder.plugin;
        this.title = builder.title;
        this.initialText = builder.initialText;
        this.validator = builder.validator;
        this.validationFailMessage = builder.validationFailMessage;
        this.onInput = builder.onInput;
        this.onCancel = builder.onCancel;
    }

    public static @NonNull Builder builder(@NonNull Plugin plugin) {
        return new Builder(plugin);
    }

    private void open(Player player) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        org.bukkit.inventory.Inventory created = plugin.getServer().createInventory(
            null,
                org.bukkit.event.inventory.InventoryType.ANVIL,
                xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage.format(title)
        );

        if (!(created instanceof AnvilInventory anvil)) {
            throw new IllegalStateException("Server did not provide an AnvilInventory for InventoryType.ANVIL");
        }
        inventory = anvil;

        ItemStack paper = ItemBuilder.of(Material.PAPER).name(initialText).build();
        inventory.setItem(0, paper);

        player.openInventory(inventory);
    }

    @EventHandler
    public void onPrepare(@NonNull PrepareAnvilEvent event) {
        if (event.getInventory() != inventory) return;
        // Zero out the repair cost / result icon so it never looks like a
        // real anvil combine — this is purely a text-capture UI.
        event.getInventory().setItem(2, null);
    }

    @EventHandler
    public void onClick(@NonNull InventoryClickEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Result slot (2) is where the renamed item would normally be
        // taken from; we intercept that click as "submit" instead of
        // letting them take an item.
        if (event.getRawSlot() == 2) {
            event.setCancelled(true);
            submit(player);
            return;
        }

        // Block taking the input item out of slot 0/1 entirely — this
        // inventory only exists to capture the rename text field.
        if (event.getRawSlot() == 0 || event.getRawSlot() == 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(@NonNull InventoryCloseEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        if (!resolved) {
            resolved = true;
            InventoryClickEvent.getHandlerList().unregister(this);
            if (onCancel != null) onCancel.accept(player);
        }

        cleanup();
    }

    private void submit(Player player) {
        if (resolved) return;

        String text = inventory.getRenameText();
        if (text == null || text.isBlank()) {
            text = initialText;
        }

        if (validator != null && !validator.test(text)) {
            if (validationFailMessage != null) {
                player.sendMessage(xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage.format(validationFailMessage));
            }
            return;
        }

        resolved = true;
        String finalText = text;
        if (onInput != null) onInput.accept(player, finalText);

        player.closeInventory();
        cleanup();
    }

    private void cleanup() {
        PrepareAnvilEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        inventory = null;
    }

    public static final class Builder {
        private final Plugin plugin;
        private String title = "<gray>Enter text";
        private String initialText = "";
        private Predicate<String> validator;
        private String validationFailMessage = "<red>Invalid input.";
        private java.util.function.BiConsumer<Player, String> onInput;
        private Consumer<Player> onCancel;

        private Builder(Plugin plugin) {
            this.plugin = plugin;
        }

        public @NonNull Builder title(@NonNull String miniMessage) {
            this.title = miniMessage;
            return this;
        }

        public @NonNull Builder initialText(@NonNull String text) {
            this.initialText = text;
            return this;
        }

        /**
         * If the predicate fails, the anvil stays open and
         * {@code failMessage} is sent instead of resolving the prompt.
         */
        public @NonNull Builder validate(@NonNull Predicate<String> validator, @NonNull String failMessage) {
            this.validator = validator;
            this.validationFailMessage = failMessage;
            return this;
        }

        public @NonNull Builder onInput(java.util.function.BiConsumer<Player, String> handler) {
            this.onInput = handler;
            return this;
        }

        public @NonNull Builder onCancel(@NonNull Consumer<Player> handler) {
            this.onCancel = handler;
            return this;
        }

        public void open(@NonNull Player player) {
            new AnvilPrompt(this).open(player);
        }
    }
}