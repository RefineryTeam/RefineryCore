package xyz.refineryteam.refinerycore.api.listener;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
@Getter
public class RefineryListener<PluginClass extends JavaPlugin> implements Listener {

    private final PluginClass plugin;

}
