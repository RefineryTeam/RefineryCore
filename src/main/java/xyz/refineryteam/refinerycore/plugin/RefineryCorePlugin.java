package xyz.refineryteam.refinerycore.plugin;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.impl.RefineryDefaultCommand;
import xyz.refineryteam.refinerycore.api.gui.handler.GUIHandlerListener;
import xyz.refineryteam.refinerycore.api.plugin.RefineryPlugin;
import xyz.refineryteam.refinerycore.plugin.banner.RefineryBanner;
import xyz.refineryteam.refinerycore.plugin.crash.CrashHints;

public final class RefineryCorePlugin extends RefineryPlugin  {

    @Getter
    private static RefineryCorePlugin instance;

    private static final int TOTAL_STEPS = 4;

    public RefineryCorePlugin() {
        instance = this;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        saveDefaultConfig();
        logMessage("<gradient:#A78BFA:#7F77DD>RefineryCore</gradient> <gray>is <yellow>●  loading<gray>...");
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        RefineryBanner.print(getConsoleSender(), getPluginMeta().getVersion());
        logMessage(" ");

        step(1, "Initializing crash handler", () -> {
            registerDefaultCrashHints();
        });

        step(2, "Detecting server implementation", () ->
                logDetail("Running on <white>" + getServerImplementation().getMinecraftVersion()));

        step(3, "Registering listeners", () ->
                getPluginManager().registerEvents(new GUIHandlerListener(this), this));

        step(4, "Registering commands", () ->
                getCommandRegistry().register(new RefineryDefaultCommand(this)));

        long took = System.currentTimeMillis() - start;
        logMessage(" ");
        logMessage("<gradient:#A78BFA:#7F77DD>RefineryCore</gradient> <gray>is now <green>●  enabled <gray>— ready in <white>" + took + "ms");
        logMessage(" ");
    }

    @Override
    public void onDisable() {
        logMessage("<dark_gray>[<gray>Shutdown<dark_gray>] <gray>Stopping RefineryCore...");
        getCommandRegistry().unregisterAll();
        logMessage("<gradient:#A78BFA:#7F77DD>RefineryCore</gradient> <gray>has been <red>●  disabled<gray>.");
        instance = null;
    }

    /**
     * Runs a single startup step with a progress-style prefix and
     * timing and prints a success line once it completes.
     */
    private void step(int index, String label, @NonNull Runnable action) {
        logMessage("<dark_gray>[<gray>" + index + "/" + TOTAL_STEPS + "<dark_gray>] <gray>" + label + "<dark_gray>...");
        long start = System.currentTimeMillis();
        try {
            action.run();
        } catch (Throwable throwable) {
            getCrashHandler().report(throwable, "during startup step: " + label);
            if (throwable instanceof RuntimeException runtimeException) throw runtimeException;
            if (throwable instanceof Error error) throw error;
            throw new RuntimeException(throwable);
        }
        long took = System.currentTimeMillis() - start;
        logMessage("<dark_gray>      <green>✔ <dark_gray>done <gray>(" + took + "ms)");
    }

    private void logDetail(String message) {
        logMessage("<dark_gray>      <gray>" + message);
    }

    /**
     * Out-of-the-box hints for the exception types plugin developers hit
     * most. Suite plugins can add their own via
     * {@code RefineryCorePlugin.getInstance().getCrashHandler().hint(...)}.
     */
    private void registerDefaultCrashHints() {
        CrashHints.init(getCrashHandler());
    }

    // No need to override the reload method as it reloads automatically if it knows this class is a JavaPlugin extended class.
}