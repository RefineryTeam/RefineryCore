package xyz.refineryteam.refinerycore.api.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.refineryteam.refinerycore.api.item.ItemSerializer;

/**
 * A complete snapshot of a player's inventory — contents, armor, and off
 * hand — serializable to a single Base64 string for storage (kits, backups,
 * punishment rollbacks, inventory viewers).
 * <p>
 * Usage:
 * <pre>{@code
 * // Capture and store
 * String saved = InventorySnapshot.capture(player).serialize();
 *
 * // Restore later (replaces current inventory)
 * InventorySnapshot.deserialize(saved).restore(player);
 * }</pre>
 */
public final class InventorySnapshot {

    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack offHand;

    private InventorySnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offHand) {
        this.storage = storage;
        this.armor = armor;
        this.offHand = offHand;
    }

    /**
     * Captures the player's full inventory state.
     */
    public static @NonNull InventorySnapshot capture(@NonNull Player player) {
        PlayerInventory inv = player.getInventory();
        return new InventorySnapshot(
                cloneAll(inv.getStorageContents()),
                cloneAll(inv.getArmorContents()),
                inv.getItemInOffHand().clone()
        );
    }

    /**
     * Restores a snapshot, replacing the player's entire inventory.
     */
    public void restore(@NonNull Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(cloneAll(storage));
        inv.setArmorContents(cloneAll(armor));
        inv.setItemInOffHand(offHand != null ? offHand.clone() : null);
    }

    /**
     * Merges a snapshot into the player's inventory without clearing it
     * first — items fill empty slots; overflow is dropped at the player's
     * feet by vanilla mechanics.
     */
    public void merge(@NonNull Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : cloneAll(storage)) {
            if (item == null) continue;
            inv.addItem(item);
        }
        // Armor/off-hand only replace empty slots during merge.
        for (int i = 0; i < armor.length && i < 4; i++) {
            if (armor[i] != null && inv.getArmorContents()[i] == null) {
                ItemStack[] contents = inv.getArmorContents();
                contents[i] = armor[i].clone();
                inv.setArmorContents(contents);
            }
        }
        if (offHand != null && inv.getItemInOffHand().isEmpty()) {
            inv.setItemInOffHand(offHand.clone());
        }
    }

    /**
     * Serializes to a compact Base64 string for database/config storage.
     */
    public @NonNull String serialize() {
        return ItemSerializer.encodeArray(storage)
                + "|" + ItemSerializer.encodeArray(armor)
                + "|" + ItemSerializer.encode(offHand);
    }

    /**
     * Deserializes a string produced by {@link #serialize()}.
     *
     * @throws IllegalArgumentException when the data is corrupt or truncated.
     */
    public static @NonNull InventorySnapshot deserialize(@NonNull String data) {
        String[] parts = data.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Corrupt inventory snapshot: expected 3 segments");
        }
        ItemStack[] storage = ItemSerializer.decodeArray(parts[0]);
        ItemStack[] armor = ItemSerializer.decodeArray(parts[1]);
        ItemStack offHand = ItemSerializer.decode(parts[2]);

        if (storage.length != 36) {
            throw new IllegalArgumentException("Corrupt inventory snapshot: storage has " + storage.length + " slots, expected 36");
        }
        return new InventorySnapshot(storage, armor, offHand);
    }

    /**
     * Convenience one-liner: capture + serialize.
     */
    public static @NonNull String captureToString(@NonNull Player player) {
        return capture(player).serialize();
    }

    /**
     * Convenience one-liner: deserialize + restore.
     */
    public static void restoreFromString(@NonNull Player player, @NonNull String data) {
        deserialize(data).restore(player);
    }

    private static ItemStack @NonNull [] cloneAll(ItemStack @Nullable [] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] != null ? items[i].clone() : null;
        }
        return out;
    }
}
