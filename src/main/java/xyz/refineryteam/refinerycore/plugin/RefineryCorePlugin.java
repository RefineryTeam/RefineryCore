package xyz.refineryteam.refinerycore.plugin;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.refineryteam.refinerycore.api.command.impl.RefineryDefaultCommand;
import xyz.refineryteam.refinerycore.api.gui.handler.GUIHandlerListener;
import xyz.refineryteam.refinerycore.api.plugin.RefineryPluginImplementation;
import xyz.refineryteam.refinerycore.plugin.banner.RefineryBanner;

public final class RefineryCorePlugin extends JavaPlugin implements RefineryPluginImplementation {

    @Getter
    private static RefineryCorePlugin instance;

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
        logMessage("<gradient:#A78BFA:#7F77DD>RefineryCore</gradient> <gray>has been <red>disabled<gray>.");
        instance = null;
    }

    // No need to override the reload method as it reloads automatically if it knows this class is a JavaPlugin extended class.
}