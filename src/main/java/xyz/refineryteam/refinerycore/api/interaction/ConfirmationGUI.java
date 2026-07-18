package xyz.refineryteam.refinerycore.api.interaction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.refineryteam.refinerycore.api.gui.RefineryGUI;
import xyz.refineryteam.refinerycore.api.item.ItemBuilder;

/**
 * A reusable yes/no confirmation screen — a green "Confirm" and red
 * "Cancel" item either side of an optional description item, so every
 * plugin stops hand-rolling its own confirmation inventory for things like
 * punishments, deletions, or purchases.
 * <p>
 * Usage:
 * <pre>{@code
 * ConfirmationGUI.builder()
 *     .title("<red>Confirm ban")
 *     .description(ItemBuilder.of(Material.PLAYER_HEAD)
 *         .name("<red>Ban " + target.getName() + "?")
 *         .lore("<gray>This action cannot be undone.")
 *         .build())
 *     .onConfirm(player -> banPlayer(target))
 *     .onCancel(player -> player.sendMessage("Cancelled."))
 *     .open(player);
 * }</pre>
 */
public final class ConfirmationGUI extends RefineryGUI {

    private static final int CONFIRM_SLOT = 11;
    private static final int DESCRIPTION_SLOT = 13;
    private static final int CANCEL_SLOT = 15;

    private final ItemStack descriptionItem;
    private final ItemStack confirmItem;
    private final ItemStack cancelItem;
    private final ConfirmationHandler onConfirm;
    private final ConfirmationHandler onCancel;
    private final boolean closeOnChoice;

    private boolean choiceMade = false;

    private ConfirmationGUI(@NonNull Builder builder) {
        super(27, builder.title);
        this.descriptionItem = builder.description;
        this.confirmItem = builder.confirmItem;
        this.cancelItem = builder.cancelItem;
        this.onConfirm = builder.onConfirm;
        this.onCancel = builder.onCancel;
        this.closeOnChoice = builder.closeOnChoice;
    }

    public static @NonNull Builder builder() {
        return new Builder();
    }

    @Override
    public void onInitialize(Player player) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int slot = 0; slot < 27; slot++) {
            setItem(slot, filler);
        }

        setItem(CONFIRM_SLOT, confirmItem, (p, event) -> choose(p, true));
        setItem(CANCEL_SLOT, cancelItem, (p, event) -> choose(p, false));

        if (descriptionItem != null) {
            setItem(DESCRIPTION_SLOT, descriptionItem);
        }
    }

    private void choose(Player player, boolean confirmed) {
        if (choiceMade) return;
        choiceMade = true;

        if (confirmed) {
            if (onConfirm != null) onConfirm.handle(player);
        } else {
            if (onCancel != null) onCancel.handle(player);
        }

        if (closeOnChoice) {
            player.closeInventory();
        }
    }

    @Override
    public void onClose(Player player) {
        // Treated as a cancel if the player closes without picking either
        // option, so callers don't need to separately listen for "walked
        // away" as distinct from an explicit cancel click.
        if (!choiceMade) {
            choiceMade = true;
            if (onCancel != null) onCancel.handle(player);
        }
    }

    @FunctionalInterface
    public interface ConfirmationHandler {
        void handle(Player player);
    }

    public static final class Builder {
        private String title = "<gray>Confirm?";
        private ItemStack description = null;
        private ItemStack confirmItem = ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                .name("<green><bold>Confirm")
                .build();
        private ItemStack cancelItem = ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                .name("<red><bold>Cancel")
                .build();
        private ConfirmationHandler onConfirm;
        private ConfirmationHandler onCancel;
        private boolean closeOnChoice = true;

        public @NonNull Builder title(@NonNull String miniMessage) {
            this.title = miniMessage;
            return this;
        }

        public @NonNull Builder description(@Nullable ItemStack item) {
            this.description = item;
            return this;
        }

        public @NonNull Builder confirmItem(@NonNull ItemStack item) {
            this.confirmItem = item;
            return this;
        }

        public @NonNull Builder cancelItem(@NonNull ItemStack item) {
            this.cancelItem = item;
            return this;
        }

        public @NonNull Builder onConfirm(@NonNull ConfirmationHandler handler) {
            this.onConfirm = handler;
            return this;
        }

        public @NonNull Builder onCancel(@NonNull ConfirmationHandler handler) {
            this.onCancel = handler;
            return this;
        }

        /**
         * If false, the inventory stays open after a choice is made — use
         * this if {@code onConfirm}/{@code onCancel} opens a different GUI
         * itself, and you don't want a flicker-close in between.
         */
        public @NonNull Builder closeOnChoice(boolean value) {
            this.closeOnChoice = value;
            return this;
        }

        public @NonNull ConfirmationGUI build() {
            return new ConfirmationGUI(this);
        }

        public void open(@NonNull Player player) {
            RefineryGUI.open(player, build());
        }
    }
}