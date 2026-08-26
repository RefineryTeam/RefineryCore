package xyz.refineryteam.refinerycore.api.item;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Base64;
import java.util.List;
import java.util.ArrayList;

/**
 * Serializes {@link ItemStack}s to compact Base64 strings so they can be
 * stored in a database column, config, or plugin message. Uses Paper's
 * native DataComponent-based serialization — full fidelity including
 * components, enchantments, and custom model data on 1.20.5+.
 * <p>
 * Usage:
 * <pre>{@code
 * String encoded = ItemSerializer.encode(stack);
 * ItemStack restored = ItemSerializer.decode(encoded);
 *
 * // Whole inventories:
 * String inv = ItemSerializer.encodeInventory(player.getInventory().getContents());
 * ItemStack[] contents = ItemSerializer.decodeInventory(inv);
 * }</pre>
 */
public final class ItemSerializer {

    private ItemSerializer() {}

    /**
     * Encodes a single stack ("null" safe — returns an empty string).
     *
     * @param stack the item to encode, or null
     * @return Base64 string, or "" for null/empty stacks
     */
    public static @NonNull String encode(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    /**
     * Decodes a single stack; empty/blank input yields null.
     *
     * @param base64 string produced by {@link #encode(ItemStack)}
     * @return the decoded stack, or null for blank input
     * @throws IllegalArgumentException when the data is corrupt
     */
    public static @Nullable ItemStack decode(@Nullable String base64) {
        if (base64 == null || base64.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (Exception e) {
            throw new IllegalArgumentException("Corrupt item data: " + e.getMessage(), e);
        }
    }

    /**
     * Encodes an array of stacks (e.g. inventory contents). Null/empty
     * slots are preserved as empty segments.
     *
     * @param stacks the items to encode, or null
     * @return semicolon-separated Base64 segments, or "" for null/empty input
     */
    public static @NonNull String encodeArray(@Nullable ItemStack @Nullable [] stacks) {
        if (stacks == null || stacks.length == 0) return "";

        StringBuilder out = new StringBuilder();
        for (ItemStack stack : stacks) {
            if (out.length() > 0) out.append(';');
            out.append(encode(stack));
        }
        return out.toString();
    }

    /**
     * Decodes an array produced by {@link #encodeArray(ItemStack[])}.
     * Empty segments become null entries.
     *
     * @param data string produced by {@link #encodeArray(ItemStack[])}
     * @return the decoded array; empty input yields an empty array
     */
    public static ItemStack @NonNull [] decodeArray(@Nullable String data) {
        if (data == null || data.isBlank()) return new ItemStack[0];

        String[] parts = data.split(";", -1);
        List<ItemStack> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            out.add(decode(part));
        }
        return out.toArray(new ItemStack[0]);
    }
}
