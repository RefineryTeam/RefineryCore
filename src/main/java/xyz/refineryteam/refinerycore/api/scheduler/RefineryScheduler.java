package xyz.refineryteam.refinerycore.api.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.TimeUnit;

public final class RefineryScheduler {

    private RefineryScheduler() {}

    public static @NonNull ScheduledTask runAsync(@NonNull JavaPlugin plugin, @NonNull Runnable runnable) {
        return plugin.getServer().getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    public static @NonNull ScheduledTask runAsyncDelayed(@NonNull JavaPlugin plugin, @NonNull Runnable runnable, long delayTicks) {
        return plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> runnable.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
    }

    public static @NonNull ScheduledTask runAsyncRepeating(@NonNull JavaPlugin plugin, @NonNull Runnable runnable, long delayTicks, long periodTicks) {
        return plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(), delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
    }

    public static @NonNull ScheduledTask runSync(@NonNull JavaPlugin plugin, @NonNull Runnable runnable) {
        return plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
    }

    public static @NonNull ScheduledTask runSyncDelayed(@NonNull JavaPlugin plugin, @NonNull Runnable runnable, long delayTicks) {
        return plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delayTicks);
    }

    public static @NonNull ScheduledTask runSyncRepeating(@NonNull JavaPlugin plugin, @NonNull Runnable runnable, long delayTicks, long periodTicks) {
        return plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), delayTicks, periodTicks);
    }

    public static ScheduledTask runForEntity(@NonNull JavaPlugin plugin, @NonNull Entity entity, @NonNull Runnable runnable) {
        return entity.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    public static ScheduledTask runForEntityRepeating(@NonNull JavaPlugin plugin, @NonNull Entity entity, @NonNull Runnable runnable, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin, task -> runnable.run(), null, delayTicks, periodTicks);
    }

    public static void cancel(@NonNull ScheduledTask task) {
        task.cancel();
    }
}