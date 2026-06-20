package com.warasugitewara.elevator;

import com.warasugitewara.elevator.command.ElevatorCommand;
import com.warasugitewara.elevator.config.ConfigManager;
import com.warasugitewara.elevator.listener.ElevatorListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ElevatorPlugin extends JavaPlugin {

	private ConfigManager configManager;

	@Override
	public void onEnable() {
		configManager = new ConfigManager(this);
		configManager.load();

		getServer().getPluginManager().registerEvents(new ElevatorListener(configManager), this);

		ElevatorCommand command = new ElevatorCommand(configManager);
		PluginCommand pluginCommand = getCommand("elevator");
		if (pluginCommand != null) {
			pluginCommand.setExecutor(command);
			pluginCommand.setTabCompleter(command);
		}

		getLogger().info("Wara's Elevators enabled.");
	}
}
