package xyz.refineryteam.refinerycore.api.plugin;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.config.RefineryConfiguration;
import xyz.refineryteam.refinerycore.api.config.RefineryConfigurationRegistry;
import xyz.refineryteam.refinerycore.api.crash.CrashHandler;

/**
 * RefineryPlugin is the base plugin class for all the plugins that use RefineryCore;
 * the main idea of this class is to implement custom methods, implementations that aren't in the {@link JavaPlugin}
 * class which Refinery uses or your plugin uses.
 * <p>
 * The class implements {@link RefineryPluginImplementation} which contains some base methods.
 *
 * <pre>
 * {@code
 * public final class ExamplePlugin extends RefineryPlugin {
 *     // You can use JavaPlugin methods here
 *     // or RefineryPluginImplementation here
 * }}
 * </pre>
 * <p>
 * <p>
 * <b>Note:</b> To use the crash handler attached to the plugin, you can use {@link RefineryPlugin#onLoad()} from super because
 * RefineryPlugin initilaizes the crash handler automatically.
*/
public class RefineryPlugin extends JavaPlugin implements RefineryPluginImplementation {

    @Getter
    private CrashHandler crashHandler;
    @Getter
    private final RefineryConfigurationRegistry configurationRegistry = RefineryConfigurationRegistry.getInstance();

    @Override
    public void onLoad() {
        crashHandler = CrashHandler.of(this);
    }

    /**
     * Reloads the plugin configurations:
     * The JavaPlugin "config.yml" configuration and the configuration registry configurations.
     * <p>
     * You can override this method to your preference.
     */
    public void reloadPlugin() {
        reloadConfig();
        getConfigurationRegistry().reload(this);
    }

    /**
     * Initializes a configuration and adds it to the configuration registry:
     * <p>
     * - It saves the configuration if it doesn't exist
     * - Adds it to the configuration registry
     * - Returns the configuration itself without changes.
     * @param configuration Configuration to initialize
     * @return the same configuration
     */
    public RefineryConfiguration initializeConfiguration(@NonNull RefineryConfiguration configuration) {
        configuration.saveDefault();
        getConfigurationRegistry().register(this, configuration);
        return configuration;
    }

}
