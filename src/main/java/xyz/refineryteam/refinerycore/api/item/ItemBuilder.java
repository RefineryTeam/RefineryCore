package xyz.refineryteam.refinerycore.api.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
            List<Component> existing = meta.lore() != null ? new java.util.ArrayList<>(Objects.requireNonNull(meta.lore())) : new java.util.ArrayList<>();
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

    // ------------------------------------------------------------------
    // Skull profile resolution
    // ------------------------------------------------------------------

    /**
     * Per-UUID cache of RESOLVED {@link PlayerProfile}s (i.e. profiles that
     * actually carry a {@code textures} property). Only completed profiles
     * ever enter this map — caching an incomplete profile would cause Paper
     * to re-issue a blocking Mojang lookup on every render.
     */
    private static final Map<UUID, PlayerProfile> SKULL_PROFILE_CACHE = new ConcurrentHashMap<>();

    /**
     * Deduplicates in-flight lookups: N callers requesting the same UUID
     * while it resolves share a single {@link CompletableFuture} instead of
     * firing N concurrent HTTP requests.
     */
    private static final Map<UUID, CompletableFuture<PlayerProfile>> IN_FLIGHT = new ConcurrentHashMap<>();

    /**
     * Failure backoff: uuid -> epoch millis of the last failed lookup.
     * While a UUID is inside its cooldown window, no further network
     * requests are made for it (prevents the 429 retry-storm / global
     * rate-limit lockout loop).
     */
    private static final Map<UUID, Long> FAILED_AT = new ConcurrentHashMap<>();

    /** How long to wait after a failed lookup before trying again. */
    private static final long FAILURE_COOLDOWN_MS = 5 * 60 * 1000L;

    /** Optional plugin used to re-render already-open inventories once a profile resolves. */
    private static Plugin plugin;

    /**
     * Must be called once on enable if you want open menus to refresh
     * automatically when an async profile lookup completes. Optional —
     * without it, the resolved texture simply appears the next time the
     * item is built.
     */
    public static void init(@NonNull Plugin pluginInstance) {
        plugin = pluginInstance;
    }

    public ItemBuilder skullOwner(@NonNull String playerName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (offlinePlayer != null) return skullOwner(offlinePlayer.getUniqueId());
        return skullOwner(Bukkit.getOfflinePlayer(playerName).getUniqueId());
    }

    public ItemBuilder skullOwner(@NonNull UUID uuid) {
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null) {
            String texture = skinsRestorerTexture(uuid);
            return texture != null ? skullRawTexture(texture) : this;
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            // Online players already have a complete, textured profile in memory.
            return skullMeta(m -> m.setPlayerProfile(online.getPlayerProfile()));
        }

        PlayerProfile cached = SKULL_PROFILE_CACHE.get(uuid);
        if (cached != null) {
            return skullMeta(m -> m.setPlayerProfile(cached));
        }

        Long failedAt = FAILED_AT.get(uuid);
        if (failedAt != null) {
            if (System.currentTimeMillis() - failedAt < FAILURE_COOLDOWN_MS) {
                // Recently failed (likely 429): do NOT hit the network again,
                // and do NOT cache anything. Render a plain head for now.
                return this;
            }
            FAILED_AT.remove(uuid); // cooldown expired, allow one retry
        }

        // Kick off (or join) a single shared lookup for this UUID.
        IN_FLIGHT.computeIfAbsent(uuid, id -> CompletableFuture.supplyAsync(() -> {
            PlayerProfile profile = Bukkit.createProfile(id);
            // complete() performs the actual Mojang session-service call.
            boolean ok = profile.complete();
            return (ok && profile.hasProperty("textures")) ? profile : null;
        }).whenComplete((profile, throwable) -> {
            IN_FLIGHT.remove(uuid);

            if (throwable != null || profile == null) {
                FAILED_AT.put(uuid, System.currentTimeMillis()); // enter backoff
                return;
            }

            SKULL_PROFILE_CACHE.put(uuid, profile); // only completed profiles cached
            FAILED_AT.remove(uuid);

            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> { /* hook: refresh open menus here if desired */ });
            }
        }));

        // The item currently being built gets a plain (Steve/Alex) head;
        // the textured version is used on the next build after resolution.
        return this;
    }

    /**
     * Reads the cached/resolved texture from SkinsRestorer without linking the
     * optional plugin at compile time. No Minecraft profile lookup is used
     * while SkinsRestorer is installed.
     */
    private static String skinsRestorerTexture(@NonNull UUID uuid) {
        try {
            Class<?> providerType = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = providerType.getMethod("get").invoke(null);
            Object playerStorage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            Object result = playerStorage.getClass()
                    .getMethod("getSkinForPlayer", UUID.class, String.class)
                    .invoke(playerStorage, uuid, name);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;
            return (String) optional.get().getClass().getMethod("getValue").invoke(optional.get());
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return null;
        }
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

    /**
     * Applies a raw Base64 texture value (the "value" field from a
     * gameprofile, as used by head databases and /give skull syntax).
     * Unlike {@link #skullTexture(String)} this does not wrap the input —
     * pass exactly what your source provides.
     */
    public ItemBuilder skullRawTexture(@NonNull String base64Texture) {
        return skullMeta(skullMeta -> {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.getProperties().add(new com.destroystokyo.paper.profile.ProfileProperty(
                    "textures", base64Texture));
            skullMeta.setPlayerProfile(profile);
        });
    }

    /**
     * Adds an enchantment glow without any visible enchantment lore.
     * Distinct from {@link #glint()} in that it works even when the item
     * already has flags applied.
     */
    public ItemBuilder glow() {
        meta(meta -> meta.setEnchantmentGlintOverride(true));
        return this;
    }

    /**
     * Explicitly disables the enchantment glint, even if enchanted.
     */
    public ItemBuilder noGlow() {
        meta(meta -> meta.setEnchantmentGlintOverride(false));
        return this;
    }

    /**
     * Replaces all lore with MiniMessage-formatted lines (convenience
     * overload of {@link #lore(List)} for string lists, e.g. straight from
     * a config section).
     */
    public ItemBuilder loreStrings(@NonNull List<String> lines) {
        return meta(meta -> meta.lore(
                lines.stream().map(EasyMiniMessage::format).toList()
        ));
    }

    /**
     * Inserts one MiniMessage line at a specific lore position.
     */
    public ItemBuilder insertLore(int index, @NonNull String line) {
        return meta(meta -> {
            List<Component> existing = meta.lore() != null ? new java.util.ArrayList<>(Objects.requireNonNull(meta.lore())) : new java.util.ArrayList<>();
            existing.add(Math.min(index, existing.size()), EasyMiniMessage.format(line));
            meta.lore(existing);
        });
    }

    public <M extends ItemMeta> ItemBuilder specificMeta(@NonNull Class<M> type, @NonNull Consumer<M> consumer) {
        ItemMeta meta = stack.getItemMeta();
        if (!type.isInstance(meta)) return this;
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
