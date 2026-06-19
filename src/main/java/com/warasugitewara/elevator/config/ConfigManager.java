package com.warasugitewara.elevator.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final Map<Material, Integer> blocks = new LinkedHashMap<>();
    private boolean soundEnabled;
    private String ascendSound;
    private String descendSound;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        blocks.clear();
        List<?> rawBlocks = config.getList("blocks");
        if (rawBlocks != null) {
            for (Object entry : rawBlocks) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Object materialKey = map.get("material");
                Object maxGapKey = map.get("max-gap");
                if (materialKey == null || maxGapKey == null) {
                    continue;
                }
                Material material = Material.matchMaterial(materialKey.toString());
                if (material == null) {
                    continue;
                }
                int maxGap = Integer.parseInt(maxGapKey.toString());
                blocks.put(material, maxGap);
            }
        }

        soundEnabled = config.getBoolean("sound.enabled", true);
        ascendSound = config.getString("sound.ascend", "minecraft:block.beacon.activate");
        descendSound = config.getString("sound.descend", "minecraft:block.beacon.deactivate");
    }

    public Map<Material, Integer> getBlocks() {
        return Collections.unmodifiableMap(blocks);
    }

    public void addOrUpdateBlock(Material material, int maxGap) {
        blocks.put(material, maxGap);
        save();
    }

    public boolean removeBlock(Material material) {
        boolean removed = blocks.remove(material) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        save();
    }

    public String getAscendSound() {
        return ascendSound;
    }

    public String getDescendSound() {
        return descendSound;
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : blocks.entrySet()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("material", entry.getKey().getKey().toString());
            map.put("max-gap", entry.getValue());
            serialized.add(map);
        }
        config.set("blocks", serialized);
        config.set("sound.enabled", soundEnabled);
        config.set("sound.ascend", ascendSound);
        config.set("sound.descend", descendSound);

        plugin.saveConfig();
    }
}
