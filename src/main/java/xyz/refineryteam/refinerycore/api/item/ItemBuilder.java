package xyz.refineryteam.refinerycore.api.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemBuilder {

    private final ItemStack stack;

    @Contract("_ -> new")
    public static @NonNull ItemBuilder of(@NonNull Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    @Contract("_, _ -> new")
    public static @NonNull ItemBuilder of(@NonNull Material material, int amount) {
        return new ItemBuilder(new ItemStack(material, amount));
    }

    @Contract("_ -> new")
    public static @NonNull ItemBuilder of(@NonNull ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    @Contract(" -> new")
    public static @NonNull ItemBuilder skull() {
        return of(Material.PLAYER_HEAD);
    }

    public ItemBuilder name(@NonNull String miniMessage) {
        return meta(meta -> meta.displayName(EasyMiniMessage.format(miniMessage)));
    }

    public ItemBuilder name(@NonNull Component component) {
        return meta(meta -> meta.displayName(component));
    }

    public ItemBuilder lore(@NonNull String... lines) {
        return meta(meta -> meta.lore(
            Arrays.stream(lines)
                .map(EasyMiniMessage::format)
                .toList()
        ));
    }

    public ItemBuilder lore(@NonNull List<Component> lines) {
        return meta(meta -> meta.lore(lines));
    }

    public ItemBuilder appendLore(@NonNull String... lines) {
        return meta(meta -> {
            List<Component> existing = meta.lore() != null ? new java.util.ArrayList<>(meta.lore()) : new java.util.ArrayList<>();
            Arrays.stream(lines).map(EasyMiniMessage::format).forEach(existing::add);
            meta.lore(existing);
        });
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    public ItemBuilder enchant(@NonNull Enchantment enchantment, int level) {
        return meta(meta -> meta.addEnchant(enchantment, level, true));
    }

    public ItemBuilder enchant(@NonNull Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
        return meta(meta -> meta.addEnchant(enchantment, level, ignoreLevelRestriction));
    }

    public ItemBuilder removeEnchant(@NonNull Enchantment enchantment) {
        return meta(meta -> meta.removeEnchant(enchantment));
    }

    public ItemBuilder flags(@NonNull ItemFlag... flags) {
        return meta(meta -> meta.addItemFlags(flags));
    }

    public ItemBuilder hideAll() {
        return flags(ItemFlag.values());
    }

    public ItemBuilder unbreakable(boolean value) {
        return meta(meta -> meta.setUnbreakable(value));
    }

    public ItemBuilder unbreakable() {
        return unbreakable(true);
    }

    public ItemBuilder glint() {
        return enchant(Enchantment.INFINITY, 1).hideAll();
    }

    public ItemBuilder customModelData(int data) {
        return meta(meta -> meta.setCustomModelData(data));
    }

    public ItemBuilder skullOwner(@NonNull String playerName) {
        return skullMeta(skullMeta -> skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName)));
    }

    public ItemBuilder skullOwner(@NonNull UUID uuid) {
        return skullMeta(skullMeta -> skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid)));
    }

    public ItemBuilder skullTexture(@NonNull String textureUrl) {
        return skullMeta(skullMeta -> {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            try {
                profile.getProperties().add(new com.destroystokyo.paper.profile.ProfileProperty(
                    "textures",
                    java.util.Base64.getEncoder().encodeToString(
                        ("{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}").getBytes()
                    )
                ));
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply skull texture: " + textureUrl, e);
            }
            skullMeta.setPlayerProfile(profile);
        });
    }

    public <M extends ItemMeta> ItemBuilder specificMeta(@NonNull Class<M> type, @NonNull Consumer<M> consumer) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !type.isInstance(meta)) return this;
        consumer.accept(type.cast(meta));
        stack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder meta(@NonNull Consumer<ItemMeta> consumer) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;
        consumer.accept(meta);
        stack.setItemMeta(meta);
        return this;
    }

    private ItemBuilder skullMeta(@NonNull Consumer<SkullMeta> consumer) {
        return specificMeta(SkullMeta.class, consumer);
    }

    public @NonNull ItemStack build() {
        return stack.clone();
    }
}