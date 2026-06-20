package com.warasugitewara.elevator;

import com.warasugitewara.elevator.command.ElevatorCommand;
import com.warasugitewara.elevator.config.ConfigManager;
import com.warasugitewara.elevator.listener.ElevatorListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ElevatorPlugin extends JavaPlugin {

	private static final long TICK_INTERVAL_TICKS = 1L;

	private ConfigManager configManager;

	@Override
	public void onEnable() {
		configManager = new ConfigManager(this);
		configManager.load();

		ElevatorListener listener = new ElevatorListener(configManager);
		getServer().getPluginManager().registerEvents(listener, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, listener::tick, 0L, TICK_INTERVAL_TICKS);

		ElevatorCommand command = new ElevatorCommand(configManager);
		PluginCommand pluginCommand = getCommand("elevator");
		if (pluginCommand != null) {
			pluginCommand.setExecutor(command);
			pluginCommand.setTabCompleter(command);
		}

		getLogger().info("Wara's Elevators enabled.");
	}
}
