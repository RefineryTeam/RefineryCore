package xyz.refineryteam.refinerycore.api.gui.animation;

import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.gui.RefineryGUI;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives periodic item updates for a {@link RefineryGUI} — animated
 * borders, blinking buttons, countdown indicators, rotating decorations.
 * <p>
 * The animator runs a repeating sync task and calls each registered
 * updater with the frame number. It stops automatically when the GUI is
 * destroyed or has no viewers left (configurable grace period), so you
 * never leak tasks.
 * <p>
 * Usage:
 * <pre>{@code
 * GUIAnimator.forGui(plugin, gui)
 *     .every(5) // ticks
 *     .updater((gui, frame) -> {
 *         int slot = BORDER_SLOTS[frame % BORDER_SLOTS.length];
 *         gui.setItem(slot, glowPane);
 *     })
 *     .start();
 * }
 * }</pre>
 */
public final class GUIAnimator {

    private final Plugin plugin;
    private final RefineryGUI gui;
    private final List<FrameUpdater> updaters = new ArrayList<>();
    private long periodTicks = 1;
    private int emptyStopAfter = 100; // frames with no viewers before auto-stop

    private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
    private long frame;
    private int emptyFrames;

    private GUIAnimator(@NonNull Plugin plugin, @NonNull RefineryGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    public static @NonNull GUIAnimator forGui(@NonNull Plugin plugin, @NonNull RefineryGUI gui) {
        return new GUIAnimator(plugin, gui);
    }

    /**
     * Sets the period between frames in ticks (default 1).
     */
    public @NonNull GUIAnimator every(long periodTicks) {
        if (periodTicks < 1) throw new IllegalArgumentException("periodTicks must be >= 1");
        this.periodTicks = periodTicks;
        return this;
    }

    /**
     * How many consecutive frames the animator may run with zero viewers
     * before stopping itself. Default 100 (~5s at 20 TPS). Set to a huge
     * value to keep animating even when nobody is looking.
     */
    public @NonNull GUIAnimator stopWhenIdleAfter(int frames) {
        this.emptyStopAfter = frames;
        return this;
    }

    /**
     * Registers a per-frame updater. Multiple updaters are supported and
     * run in registration order.
     */
    public @NonNull GUIAnimator updater(@NonNull FrameUpdater updater) {
        updaters.add(updater);
        return this;
    }

    /**
     * Starts the animation. Safe to call twice — the second call is ignored
     * while an animation is already running.
     */
    public synchronized void start() {
        if (task != null && !task.isCancelled()) return;

        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                t -> tick(),
                periodTicks,
                periodTicks
        );
    }

    private void tick() {
        if (gui.isDestroyed()) {
            stop();
            return;
        }

        var inventory = gui.getInventory();
        if (inventory == null || inventory.getViewers().isEmpty()) {
            if (++emptyFrames >= emptyStopAfter) stop();
            return;
        }
        emptyFrames = 0;

        frame++;
        for (FrameUpdater updater : updaters) {
            try {
                updater.update(gui, frame);
            } catch (Exception e) {
                plugin.getLogger().warning("GUI animator updater threw: " + e.getMessage());
            }
        }
    }

    /**
     * Stops the animation. Called automatically on destroy / idle timeout.
     */
    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * @return true while the animation is running.
     */
    public synchronized boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    /**
     * A single animation step. {@code frame} starts at 1 and increments
     * each tick the animator fires.
     */
    @FunctionalInterface
    public interface FrameUpdater {
        void update(@NonNull RefineryGUI gui, long frame);
    }
}
