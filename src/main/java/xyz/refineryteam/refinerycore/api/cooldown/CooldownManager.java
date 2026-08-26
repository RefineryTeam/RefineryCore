package xyz.refineryteam.refinerycore.api.cooldown;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks arbitrary cooldowns keyed by (namespace, key, subject). Namespace
 * is typically the owning plugin/feature (e.g. "combat", "kits"), so two
 * plugins can use the same subject/key without colliding.
 * <p>
 * Usage:
 * <pre>{@code
 * CooldownManager cooldowns = new CooldownManager();
 *
 * if (cooldowns.isOnCooldown("kits", "starter", player.getUniqueId())) {
 *     long remaining = cooldowns.remainingSeconds("kits", "starter", player.getUniqueId());
 *     context.reply("<red>Wait " + remaining + "s before using this again.");
 *     return;
 * }
 *
 * cooldowns.set("kits", "starter", player.getUniqueId(), Duration.ofMinutes(30));
 * }</pre>
 */
public final class CooldownManager {

    private final Map<String, Long> expiries = new ConcurrentHashMap<>();

    public void set(@NonNull String namespace, @NonNull String key, @NonNull UUID subject, @NonNull Duration duration) {
        expiries.put(id(namespace, key, subject), System.currentTimeMillis() + duration.toMillis());
    }

    public void setSeconds(@NonNull String namespace, @NonNull String key, @NonNull UUID subject, long seconds) {
        set(namespace, key, subject, Duration.ofSeconds(seconds));
    }

    public boolean isOnCooldown(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        Long expiry = expiries.get(id(namespace, key, subject));
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            expiries.remove(id(namespace, key, subject));
            return false;
        }
        return true;
    }

    public long remainingMillis(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        Long expiry = expiries.get(id(namespace, key, subject));
        if (expiry == null) return 0L;
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    public long remainingSeconds(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        return remainingMillis(namespace, key, subject) / 1000L;
    }

    public void clear(@NonNull String namespace, @NonNull String key, @NonNull UUID subject) {
        expiries.remove(id(namespace, key, subject));
    }

    public void clearAll(@NonNull UUID subject) {
        String suffix = ":" + subject;
        expiries.keySet().removeIf(k -> k.endsWith(suffix));
    }

    /**
     * Attempts to start the cooldown only if the subject isn't already on
     * one. Returns {@code true} if the cooldown was applied (i.e., the
     * action should proceed), {@code false} if it's still on cooldown.
     * <p>
     * Atomic: uses a single {@code compute} so two concurrent callers can
     * never both pass the check.
     */
    public boolean tryAcquire(@NonNull String namespace, @NonNull String key, @NonNull UUID subject, @NonNull Duration duration) {
        String id = id(namespace, key, subject);
        long now = System.currentTimeMillis();
        long newExpiry = now + duration.toMillis();

        boolean[] acquired = {false};
        expiries.compute(id, (k, existing) -> {
            if (existing != null && now < existing) return existing; // still on cooldown
            acquired[0] = true;
            return newExpiry;
        });
        return acquired[0];
    }

    /**
     * Removes every expired entry. Expired keys are otherwise only cleaned
     * lazily when the same key is queried again; call this periodically
     * (e.g. from a slow repeating task) to avoid unbounded growth from
     * one-shot cooldowns that are never re-checked.
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        expiries.values().removeIf(expiry -> now >= expiry);
    }

    @Contract(pure = true)
    private @NonNull String id(String namespace, String key, UUID subject) {
        return namespace + ":" + key + ":" + subject;
    }
}