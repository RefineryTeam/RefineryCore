package xyz.refineryteam.refinerycore.plugin;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.refineryteam.refinerycore.api.command.impl.RefineryDefaultCommand;
import xyz.refineryteam.refinerycore.api.crash.CrashHandler;
import xyz.refineryteam.refinerycore.api.crash.CrashHint;
import xyz.refineryteam.refinerycore.api.gui.handler.GUIHandlerListener;
import xyz.refineryteam.refinerycore.api.plugin.RefineryPluginImplementation;
import xyz.refineryteam.refinerycore.plugin.banner.RefineryBanner;

public final class RefineryCorePlugin extends JavaPlugin implements RefineryPluginImplementation {

    @Getter
    private static RefineryCorePlugin instance;

    @Getter
    private CrashHandler crashHandler;

    public RefineryCorePlugin() {
        instance = this;
    }

    @Override
    public void onLoad() {
        saveDefaultConfig();
        logMessage("<dark_gray>→ <gray>Configuration loaded");
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        RefineryBanner.print(getConsoleSender(), getPluginMeta().getVersion());

        logMessage("<dark_gray>→ <gray>Initializing crash handler...");
        this.crashHandler = CrashHandler.of(this);
        registerDefaultCrashHints();
        logMessage("<dark_gray>  <green>✔ <gray>Crash handler ready");

        logMessage("<dark_gray>→ <gray>Detecting server implementation...");
        logMessage("<dark_gray>  <green>✔ <gray>Running on <white>" + getServerImplementation().getMinecraftVersion());

        logMessage("<dark_gray>→ <gray>Registering listeners...");
        getPluginManager().registerEvents(new GUIHandlerListener(this), this);
        logMessage("<dark_gray>  <green>✔ <gray>GUI handler registered");

        logMessage("<dark_gray>→ <gray>Registering commands...");
        getCommandRegistry().register(new RefineryDefaultCommand(this));
        logMessage("<dark_gray>  <green>✔ <gray>/refinerycore registered");

        long took = System.currentTimeMillis() - start;
        logMessage(" ");
        logMessage("<gradient:#A78BFA:#7F77DD>RefineryCore</gradient> <gray>is now <green>online <gray>— ready in <white>" + took + "ms");
        logMessage(" ");
    }

    @Override
    public void onDisable() {
        logMessage("<dark_gray>→ <gray>Shutting down RefineryCore...");
        getCommandRegistry().unregisterAll();
        logMessage("<gradient:#A78BFA:#7F77DD>RefineryCore</gradient> <gray>has been <red>disabled<gray>.");
        instance = null;
    }

    /**
     * Out-of-the-box hints for the exception types plugin developers hit
     * most. Suite plugins can add their own via
     * {@code RefineryCorePlugin.getInstance().getCrashHandler().hint(...)}.
     */
    private void registerDefaultCrashHints() {
        crashHandler.hint(CrashHint.of()
                .matchesType(java.sql.SQLException.class)
                .title("Database error")
                .context("SQL State", e -> e instanceof java.sql.SQLException sql ? sql.getSQLState() : null)
                .context("Error Code", e -> e instanceof java.sql.SQLException sql ? String.valueOf(sql.getErrorCode()) : null)
                .help(
                        "Is the database server reachable? Try connecting from this machine with the same credentials.",
                        "Check database.host / database.port / database.name in your config.",
                        "Verify database.username and database.password are correct.",
                        "If using SQLite/H2, make sure the data folder exists and is writable.",
                        "Using MySQL/MariaDB? Confirm the user has privileges on the database."
                )
                .build());

        crashHandler.hint(CrashHint.of()
                .matchesType(java.io.IOException.class)
                .title("File I/O error")
                .help(
                        "Check that the file/folder exists and isn't locked by another process.",
                        "Verify disk space — a full disk causes write failures.",
                        "On Linux, check folder permissions (the server user needs read/write).",
                        "Is an antivirus or backup tool holding the file open?"
                )
                .build());

        crashHandler.hint(CrashHint.of()
                .matchesType(ClassNotFoundException.class)
                .title("Missing class")
                .context("Class", e -> e.getMessage())
                .help(
                        "A required library or dependency is missing from the classpath.",
                        "If this is a soft dependency, is the providing plugin installed and enabled?",
                        "Did you shade all needed libraries into your plugin jar?",
                        "Version mismatch? A library compiled against a newer Java version won't load on older JVMs."
                )
                .build());

        crashHandler.hint(CrashHint.of()
                .matchesType(NoSuchMethodError.class)
                .title("API version mismatch")
                .context("Detail", Throwable::getMessage)
                .help(
                        "A plugin was compiled against a different version of a shared API than is loaded.",
                        "Update or downgrade the plugins involved until versions agree.",
                        "If this involves RefineryCore, make sure all suite plugins use compatible RefineryCore builds.",
                        "Full restart (not /reload) is required after swapping jars."
                )
                .build());

        crashHandler.hint(CrashHint.of()
                .matchesType(IllegalStateException.class)
                .title("Illegal state")
                .help(
                        "This usually means an operation ran at a bad time or on the wrong thread.",
                        "Was this called during onEnable/onDisable when the world or players weren't ready?",
                        "Folia/Paper regions: Bukkit API calls must run on the owning region thread — see TaskChain.",
                        "Read the stack trace below to find which plugin made the call."
                )
                .build());

        crashHandler.hint(CrashHint.of()
                .matchesType(NullPointerException.class)
                .title("Null pointer")
                .help(
                        "Something was null that shouldn't have been — the stack trace shows exactly where.",
                        "Common culprits: a config value missing (null after load), an offline player lookup,",
                        "or an event field accessed after the event completed.",
                        "If it happens in a RefineryCore GUI handler, the GUI may have been destroyed mid-click."
                )
                .build());
    }

    // No need to override the reload method as it reloads automatically if it knows this class is a JavaPlugin extended class.
}